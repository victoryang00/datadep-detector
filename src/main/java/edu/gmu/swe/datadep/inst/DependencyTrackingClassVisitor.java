package edu.gmu.swe.datadep.inst;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.commons.LocalVariablesSorter;
import org.objectweb.asm.tree.FieldNode;

import edu.gmu.swe.datadep.DependencyInfo;
import edu.gmu.swe.datadep.DependencyInstrumented;
import edu.gmu.swe.datadep.Enumerations;
import edu.gmu.swe.datadep.Instrumenter;

public class DependencyTrackingClassVisitor extends ClassVisitor {

	// Logging
	// public final static List<Pattern> fieldsLogged = Arrays.asList(new
	// Pattern[] { Pattern.compile(".*crystal.*") });
	public final static List<Pattern> fieldsLogged = new ArrayList<>();

	boolean skipFrames = false;

	public DependencyTrackingClassVisitor(ClassVisitor _cv, boolean skipFrames) {
		super(Opcodes.ASM5, _cv);
		this.skipFrames = skipFrames;
	}

	String className;
	boolean isClass = false;

	private boolean patchLDCClass;
	private boolean addTaintField = true;

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		// make sure class is not private
		access = access & ~Opcodes.ACC_PRIVATE;
		access = access | Opcodes.ACC_PUBLIC;
		this.isClass = (access & Opcodes.ACC_INTERFACE) == 0;
		this.className = name;
		this.patchLDCClass = (version & 0xFFFF) < Opcodes.V1_5;

		// Do not add on the class the taint field, so their instances will not
		// be tainted
		if (!superName.equals("java/lang/Object") && !Instrumenter.isIgnoredClass(superName)) {
			addTaintField = false;
		}

		// If we completely ignore Enums and Strings this is not needed anymore
		// !
		// Do not add on the class the taint field to String
		// if (String.class.getName().equals(this.className.replace("/", ".")))
		// {
		// addTaintField = false;
		// }
		// // Do not add on the class the taint field to enumerations
		// if (Enumerations.get().contains(this.className.replace("/", "."))) {
		// addTaintField = false;
		// }

		// Tainting interface

		// Do not let Enum and String implement the DependencyInstrumented
		// interface
		// if (String.class.getName().equals(this.className.replace("/", ".")))
		// {
		// System.out.println("Do not add Tainting interface to " +
		// this.className);
		// super.visit(version, access, name, signature, superName, interfaces);
		// return;
		// }
		// // Do not add on the class the taint field to enumerations
		// if (Enumerations.get().contains(this.className.replace("/", "."))) {
		// System.out.println("Do not add Tainting interface to " +
		// this.className);
		// super.visit(version, access, name, signature, superName, interfaces);
		// return;
		// }

		if (Instrumenter.isIgnoredClass(name)) {
			System.out.println(">>>> WARN THIS SHOULD NEVER HAPPEN !");

		}

		if (!Instrumenter.isIgnoredClass(name) && isClass) { // && (access &
																// Opcodes.ACC_ENUM)
																// == 0) {
			String[] iface = new String[interfaces.length + 1];
			System.arraycopy(interfaces, 0, iface, 0, interfaces.length);
			iface[interfaces.length] = Type.getInternalName(DependencyInstrumented.class);
			interfaces = iface;
			if (signature != null)
				signature = signature + Type.getDescriptor(DependencyInstrumented.class);

		}
		// else {
		// System.out.println("Do not add Tainting interface to " +
		// this.className);
		// }
		super.visit(version, access, name, signature, superName, interfaces);
	}

	// With the original type associated
	LinkedList<Entry<FieldNode, Type>> moreFields = new LinkedList<Entry<FieldNode, Type>>();

	@Override
	public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {

		Type t = Type.getType(desc);

		boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
		boolean isPrimitive = (t.getSort() != Type.ARRAY && t.getSort() != Type.OBJECT);
		boolean isFinal = (access & Opcodes.ACC_FINAL) != 0;

		boolean isEnum = ((access & Opcodes.ACC_ENUM) != 0)
				|| (Enumerations.get().contains(t.getClassName().replace("/", ".")));
		boolean isString = desc.equals("Ljava/lang/String;");

		// Enum elements are fields of type enums and their type is the same as
		// the containing class
		boolean isEnumElement = isEnum
				&& t.getClassName().replace("/", ",").equals(this.className.replaceAll("/", "."));

		// Blacklisted fields - This cause problems:
		// Exception in thread "Thread-1" java.lang.NoSuchFieldError:
		// $assertionsDisabled__DEPENDENCY_INFO
		// at crystal.server.TestConstants.<clinit>(TestConstants.java:10)
		//
		// if( isPrimitive && isFinal && ( name.equals("$assertionsDisabled") ||
		// name.equals("serialVersionUID"))){
		// return super.visitField(access, name, desc, signature, value);
		// }
		// System.out.println("DependencyTrackingClassVisitor.visitField() " +
		// this.className + "." + name);

		// Static fields of any type requires an additional static taint field
		// Final and primitive are constant, however, the test which initializes
		// them might be relevant so we keep them
		//
		// if (isFinal && isPrimitive) {
		// System.out.println(">>> DependencyTrackingClassVisitor.visitField()
		// Skip Final Static Primitive "
		// + this.className + "." + name);
		// } else
		//
		if (isEnumElement) {
			System.out.println(">>>>> WARNING THIS SHOULD NEVER HAPPEN. Skip ENUM ITEM " + this.className + "." + name);

		} else if (isStatic) {

			// System.out.println(
			// "DependencyTrackingClassVisitor.visitField() " + className + "."
			// + name + "__DEPENDENCY_INFO");
			moreFields.add(new AbstractMap.SimpleEntry<FieldNode, Type>(new FieldNode(access,
					name + "__DEPENDENCY_INFO", Type.getDescriptor(DependencyInfo.class), null, null), t));
		} else {
			// Primitive types require an additional taint field. Primitive
			// types are ALWAYS != null
			if (isPrimitive) {
				moreFields.add(new AbstractMap.SimpleEntry<FieldNode, Type>(new FieldNode(access,
						name + "__DEPENDENCY_INFO", Type.getDescriptor(DependencyInfo.class), null, null), t));
				//
			} else
			// String types require an additional taint field. String
			// types can be null
			if (isString) {
				moreFields.add(new AbstractMap.SimpleEntry<FieldNode, Type>(new FieldNode(access,
						name + "__DEPENDENCY_INFO", Type.getDescriptor(DependencyInfo.class), null, null), t));
			} else
			// Enum field (but not EnumItems) require an additional taint field.
			// Enum fields
			// types can be null
			if (isEnum) {
				moreFields.add(new AbstractMap.SimpleEntry<FieldNode, Type>(new FieldNode(access,
						name + "__DEPENDENCY_INFO", Type.getDescriptor(DependencyInfo.class), null, null), t));
			}
		}
		// resume the original visit
		return super.visitField(access, name, desc, signature, value);

	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

		AnalyzerAdapter an = null;
		if (!skipFrames) {
			an = new AnalyzerAdapter(className, access, name, desc, mv);
			mv = an;
		}

		RWTrackingMethodVisitor rtmv = new RWTrackingMethodVisitor(mv, patchLDCClass, className, access, name, desc);
		mv = rtmv;
		if (!skipFrames) {
			rtmv.setAnalyzer(an);
		}
		LocalVariablesSorter lvs = new LocalVariablesSorter(access, desc, mv);
		rtmv.setLVS(lvs);
		return lvs;
	}

	@Override
	public void visitEnd() {

		// Register the synthetic fields with the class
		for (Entry<FieldNode, Type> e : moreFields) {
			e.getKey().accept(cv);
		}
		if (isClass) {

			// Add field to store dep info
			if (addTaintField) {
				super.visitField(Opcodes.ACC_PUBLIC, "__DEPENDENCY_INFO", Type.getDescriptor(DependencyInfo.class),
						null, null);
			}

			// Implement the getDEPENDENCY_INFO method. Initialize the object if
			// null
			MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "getDEPENDENCY_INFO",
					"()" + Type.getDescriptor(DependencyInfo.class), null, null);
			mv.visitCode();

			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitFieldInsn(Opcodes.GETFIELD, className, "__DEPENDENCY_INFO",
					Type.getDescriptor(DependencyInfo.class));
			mv.visitInsn(Opcodes.DUP);
			Label ok = new Label();
			mv.visitJumpInsn(Opcodes.IFNONNULL, ok);
			mv.visitInsn(Opcodes.POP);

			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitTypeInsn(Opcodes.NEW, Type.getInternalName(DependencyInfo.class));
			mv.visitInsn(Opcodes.DUP_X1);
			mv.visitInsn(Opcodes.DUP);
			mv.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(DependencyInfo.class), "<init>", "()V",
					false);
			mv.visitFieldInsn(Opcodes.PUTFIELD, className, "__DEPENDENCY_INFO",
					Type.getDescriptor(DependencyInfo.class));

			// LOG THE DEP INFO FOR THIS CLASS
			for (Pattern p : fieldsLogged) {
				if (p.matcher(this.className).matches()) {
					logMe(mv, this.className, this.className.replaceAll("/", "."));
					break;
				}
			}

			mv.visitLabel(ok);
			mv.visitFrame(Opcodes.F_FULL, 1, new Object[] { className }, 1,
					new Object[] { Type.getInternalName(DependencyInfo.class) });
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();

			// Implement Additional fields initialization

			mv = super.visitMethod(Opcodes.ACC_PUBLIC, "__initPrimDepInfo", "()V", null, null);
			mv.visitCode();
			for (Entry<FieldNode, Type> e : moreFields) {
				FieldNode fn = e.getKey();
				Type t = e.getValue();

				// Non static fields inside __initPrimeDepInfo
				if ((fn.access & Opcodes.ACC_STATIC) == 0) {

					// Create Taint data
					mv.visitVarInsn(Opcodes.ALOAD, 0);
					mv.visitTypeInsn(Opcodes.NEW, Type.getInternalName(DependencyInfo.class));
					// Call constructor
					mv.visitInsn(Opcodes.DUP);
					mv.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(DependencyInfo.class), "<init>",
							"()V", false);

					if ((Enumerations.get().contains(t.getClassName().replaceAll("/", ".")))
							|| String.class.getName().equals(t.getClassName().replaceAll("/", "."))) {
						// System.out.println("DependencyTrackingClassVisitor.visitEnd()
						// Do not propage write for "
						// + fn.name + " corresponding type " +
						// t.getClassName());
						// Call write
						// mv.visitInsn(Opcodes.DUP);
						// mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
						// Type.getInternalName(DependencyInfo.class), "reset",
						// "()V", false);
						// Call write
					} else {
						// True Primitives are initialized by default the moment
						// they are declared, hence, they are written

						///
						//
						//
						// mv.visitFieldInsn(Opcodes.GETSTATIC,
						// "java/lang/System", "out", "Ljava/io/PrintStream;");
						// mv.visitLdcInsn("Calling write to " +
						// className.replaceAll("/", ".") + "." + fn.name);
						// mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
						// "java/io/PrintStream", "println",
						// "(Ljava/lang/String;)V", false);
						//
						///
						//
						mv.visitInsn(Opcodes.DUP);
						mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(DependencyInfo.class), "write",
								"()V", false);
					}

					// Check if the complete name of the fields matches !
					String fieldFQN = this.className.replaceAll("/", ".") + "."
							+ fn.name.replace("__DEPENDENCY_INFO", "");

					for (Pattern p : fieldsLogged) {
						if (p.matcher(fieldFQN).matches()) {
							logMe(mv, fn, fieldFQN);
						}
					}

					// Finally store the value in the field
					mv.visitFieldInsn(Opcodes.PUTFIELD, className, fn.name, Type.getDescriptor(DependencyInfo.class));
				}
			}
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
		super.visitEnd();
	}

	private void logMe(MethodVisitor mv, FieldNode fn, String msg) {
		System.out.println(
				">>>> DependencyTrackingClassVisitor.visitEnd() Enabling LOG for FN " + fn.name + " as " + msg);
		mv.visitInsn(Opcodes.DUP);
		// Input to next method invocation
		mv.visitLdcInsn(msg);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(DependencyInfo.class), "logMe",
				"(Ljava/lang/String;)V", false);
	}

	private void logMe(MethodVisitor mv, String className, String msg) {
		System.out.println(
				">>>> DependencyTrackingClassVisitor.visitEnd() Enabling LOG for  Class " + className + " as " + msg);
		mv.visitInsn(Opcodes.DUP);
		// Input to next method invocation
		mv.visitLdcInsn(msg);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(DependencyInfo.class), "logMe",
				"(Ljava/lang/String;)V", false);
	}
}
