package edu.gmu.swe.datadep;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.SerialVersionUIDAdder;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceClassVisitor;

import edu.gmu.swe.datadep.inst.DependencyTrackingClassVisitor;
import edu.gmu.swe.datadep.inst.MockingClassVisitor;

public class RWDependencyClassFileTransformer implements ClassFileTransformer {

	static boolean innerException = false;
	static final boolean DEBUG = System.getProperties().containsKey("debug");

	public byte[] transform(ClassLoader loader, final String className2, Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
		ClassReader cr = new ClassReader(classfileBuffer);
		String className = cr.getClassName();
		innerException = false;
		//
		if (Instrumenter.isIgnoredClass(className)) {
			// System.out.println("RWDependencyClassFileTransformer.transform()
			// Ignore " + className);
			return classfileBuffer;
		}

		ClassNode cn = new ClassNode();
		cr.accept(cn, ClassReader.SKIP_CODE);
		boolean skipFrames = false;
		if (cn.version >= 100 || cn.version < 50)
			skipFrames = true;

		/*
		 * When a class is mocked we do not care about it, but, because it might
		 * be a subclass of an instrumented class we have to tag it with an
		 * additional interface. In any case, we return at this point to avoid
		 * re-applying the RW machinery
		 */
		TraceClassVisitor cv = null;
		//
		if (cn.interfaces != null) {
			for (Object s : cn.interfaces) {
				if (Type.getInternalName(MockedClass.class).equals(s)) {
					System.out.println(
							"RWDependencyClassFileTransformer.transform() " + className + " already a MockedClass");
					return classfileBuffer;
				}
			}
		}
		if (Instrumenter.isMockedClass(cn.name)) {
			try {
				ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);

				cr.accept(
						// new CheckClassAdapter(
						new SerialVersionUIDAdder(new MockingClassVisitor(cw, skipFrames))
						// )
						, ClassReader.EXPAND_FRAMES);

				return cw.toByteArray();
			} catch (Throwable ex) {
				ex.printStackTrace();
				cv = new TraceClassVisitor(null, null);
				try {
					cr.accept(new CheckClassAdapter(new SerialVersionUIDAdder(new MockingClassVisitor(cv, skipFrames))),
							ClassReader.EXPAND_FRAMES);
				} catch (Throwable ex2) {
					ex2.printStackTrace();
				}
				System.exit(-1);
				return new byte[0];

			}
		}

		if (cn.interfaces != null) {
			for (Object s : cn.interfaces) {
				if (Type.getInternalName(DependencyInstrumented.class).equals(s))
					return classfileBuffer;
			}
		}
		for (Object mn : cn.methods)
			/*
			 * is the call && !Instrumenter.isMockedClass(className) fine here ?
			 */
			if (((MethodNode) mn).name.equals("getDEPENDENCY_INFO") && !Instrumenter.isMockedClass(className))
				return classfileBuffer;
		try {

			ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);

			cr.accept(
					// new CheckClassAdapter(
					new SerialVersionUIDAdder(new DependencyTrackingClassVisitor(cw, skipFrames))
					// )
					, ClassReader.EXPAND_FRAMES);

			if (DEBUG) {
				File debugDir = new File("debug");
				if (!debugDir.exists())
					debugDir.mkdir();
				File f = new File("debug/" + className.replace("/", ".") + ".class");
				FileOutputStream fos = new FileOutputStream(f);
				fos.write(cw.toByteArray());
				fos.close();
			}
			return cw.toByteArray();
		} catch (Throwable ex) {
			ex.printStackTrace();
			cv = new TraceClassVisitor(null, null);
			try {
				cr.accept(
						new CheckClassAdapter(
								new SerialVersionUIDAdder(new DependencyTrackingClassVisitor(cv, skipFrames))),
						ClassReader.EXPAND_FRAMES);
			} catch (Throwable ex2) {
				ex2.printStackTrace();
			}
			System.err.println("method so far:");
			if (!innerException) {
				PrintWriter pw = null;
				try {
					pw = new PrintWriter(new FileWriter("lastClass.txt"));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				cv.p.print(pw);
				pw.flush();
			}
			System.out.println("Saving " + className);
			File f = new File("debug/" + className.replace("/", ".") + ".class");
			try {
				FileOutputStream fos = new FileOutputStream(f);
				fos.write(classfileBuffer);
				fos.close();
			} catch (Exception ex2) {
				ex.printStackTrace();
			}
			System.exit(-1);
			return new byte[0];

		}
	}
}
