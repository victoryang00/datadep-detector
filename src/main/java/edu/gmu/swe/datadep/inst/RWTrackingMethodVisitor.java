package edu.gmu.swe.datadep.inst;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.commons.LocalVariablesSorter;

import edu.gmu.swe.datadep.DependencyInfo;
import edu.gmu.swe.datadep.Enumerations;
import edu.gmu.swe.datadep.Instrumenter;

/**
 * Inserts instrumentation to record heap reads and writes
 *
 * @author jon
 *
 */
public class RWTrackingMethodVisitor extends AdviceAdapter implements Opcodes {
	private boolean patchLDCClass;
	private String clazz;
	private boolean inUninitializedSuper = false;
	//
	private boolean isStaticInitializer = false;
	//
	private boolean isBlackListed = false;

	private String methodName;

	public RWTrackingMethodVisitor(MethodVisitor mv, boolean patchLDCClass, String className, int acc,
			String methodName, String desc) {
		super(Opcodes.ASM5, mv, acc, methodName, desc);
		this.patchLDCClass = patchLDCClass;
		this.clazz = className;
		this.methodName = methodName;
		//
		this.inUninitializedSuper = "<init>".equals(methodName);
		this.isStaticInitializer = "<clinit>".equals(methodName);
	}

	// TODO: most likely this patch to deal with clinit must be made
	// contextual... we do not want to disable/re-enable IN_CAPTURE if this is
	// already enables/disabled. Probably we shall add another state variable
	// with a countlatch for nested clinit....
	@Override
	protected void onMethodEnter() {
		if (this.isStaticInitializer) {
			// TODO Disable collection while inside this method, might not work
			// for multi-threaded !
			super.visitInsn(ICONST_1);
			super.visitFieldInsn(PUTSTATIC, "edu/gmu/swe/datadep/DependencyInfo", "IN_CAPTURE", "Z");
		}
		//
		if (this.inUninitializedSuper) {
			this.inUninitializedSuper = false;
			super.visitVarInsn(ALOAD, 0);
			super.visitMethodInsn(INVOKEVIRTUAL, clazz, "__initPrimDepInfo", "()V", false);
		}
		this.inUninitializedSuper = false;

		if (this.isBlackListed) {
			System.out
					.println("RWTrackingMethodVisitor.onMethodEnter() BlackListed method " + clazz + "." + methodName);
		}

	}

	@Override
	protected void onMethodExit(int opcode) {
		if (this.isStaticInitializer) {
			// TODO Disable collection while inside this method, might not work
			// for multi-threaded !
			super.visitInsn(ICONST_0);
			super.visitFieldInsn(PUTSTATIC, "edu/gmu/swe/datadep/DependencyInfo", "IN_CAPTURE", "Z");
		}

		if (this.isBlackListed) {
			System.out.println("RWTrackingMethodVisitor.onMethodExit() BlackListed method " + clazz + "." + methodName);
		}
		super.onMethodExit(opcode);
	}

	public void visitLdcInsn(Object cst) {
		if (cst instanceof Type && patchLDCClass) {
			super.visitLdcInsn(((Type) cst).getInternalName().replace("/", "."));
			super.visitInsn(Opcodes.ICONST_0);
			super.visitLdcInsn(clazz.replace("/", "."));
			super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Class", "forName",
					"(Ljava/lang/String;)Ljava/lang/Class;", false);
			super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getClassLoader",
					"()Ljava/lang/ClassLoader;", false);
			super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Class", "forName",
					"(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", false);
		} else
			super.visitLdcInsn(cst);
	}

	int tmpLField = -1;
	int tmpDField = -1;

	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
		//
		if (name.contains("keySet")) {
			// System.out
			// .println("RWTrackingMethodVisitor.visitMethodInsn() Disable
			// collection for " + owner + "." + name);
			// Disable collection
			super.visitInsn(ICONST_1);
			super.visitFieldInsn(PUTSTATIC, "edu/gmu/swe/datadep/DependencyInfo", "IN_CAPTURE", "Z");
		}
		super.visitMethodInsn(opcode, owner, name, desc, itf);
		if (name.contains("keySet")) {
			// System.out.println("RWTrackingMethodVisitor.visitMethodInsn()
			// Reenable collection " + owner + "." + name);
			// Disable collection
			super.visitInsn(ICONST_0);
			super.visitFieldInsn(PUTSTATIC, "edu/gmu/swe/datadep/DependencyInfo", "IN_CAPTURE", "Z");
		}

	}

	@Override
	public void visitInsn(int opcode) {
		switch (opcode) {
		// TODO What's this ? Wheneve a class is loaded call a read on it? or
		// instantiated ?
		case AALOAD:
			super.visitInsn(opcode);
			super.visitInsn(DUP);
			super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(DependencyInfo.class), "read",
					"(Ljava/lang/Object;)V", false);
			return;
		// case LASTORE:
		// if(tmpLField < 0)
		// tmpLField = lvs.newLocal(Type.LONG_TYPE);
		// super.visitVarInsn(LSTORE, tmpLField);
		// super.visitInsn(SWAP);
		// super.visitInsn(DUP);
		// super.visitMethodInsn(INVOKESTATIC,
		// Type.getInternalName(DependencyInfo.class), "write",
		// "(Ljava/lang/Object;)V", false);
		// super.visitInsn(SWAP);
		// super.visitVarInsn(LLOAD, tmpLField);
		// break;
		// case DASTORE:
		// if(tmpDField < 0)
		// tmpDField = lvs.newLocal(Type.DOUBLE_TYPE);
		// super.visitVarInsn(DSTORE, tmpDField);
		// super.visitInsn(SWAP);
		// super.visitInsn(DUP);
		// super.visitMethodInsn(INVOKESTATIC,
		// Type.getInternalName(DependencyInfo.class), "write",
		// "(Ljava/lang/Object;)V", false);
		// super.visitInsn(SWAP);
		// super.visitVarInsn(DLOAD, tmpDField);
		// break;
		// case AASTORE:
		// case IASTORE:
		// case SASTORE:
		// case CASTORE:
		// case BASTORE:
		// case FASTORE:
		// super.visitInsn(DUP2_X1);
		// super.visitInsn(POP2);
		// super.visitInsn(DUP);
		// super.visitMethodInsn(INVOKESTATIC,
		// Type.getInternalName(DependencyInfo.class), "write",
		// "(Ljava/lang/Object;)V", false);
		// super.visitInsn(DUP_X2);
		// super.visitInsn(POP);
		// break;
		}
		super.visitInsn(opcode);
	}

	@Override
	public void visitFieldInsn(int opcode, String owner, String name, String desc) {

		// If the container class is ignored, we ignore the field
		if (Instrumenter.isIgnoredClass(owner)) {
			super.visitFieldInsn(opcode, owner, name, desc);
			return;
		}
		switch (opcode) {
		case GETFIELD: // On FIELD read/access

			if (isStaticInitializer) {
				// super.visitFieldInsn(PUTFIELD, "java/lang/System", "out",
				// "Ljava/io/PrintStream;");
				// super.visitLdcInsn(
				// "<clinit> Forbid write to " + owner.replaceAll("/", ".") +
				// "." + name + " in clinit");
				// super.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream",
				// "println", "(Ljava/lang/String;)V",
				// false);
				super.visitFieldInsn(opcode, owner, name, desc);
				return;
			}

			// Not sure what happens for primitives, Strings, and Enums ??
			// [ objectref ] -->
			super.visitInsn(DUP);
			// [ objectref, objectref ] -->
			super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(DependencyInfo.class), "read",
					"(Ljava/lang/Object;)V", false);
			// [ objectref ] -->

			Type t = Type.getType(desc);
			switch (t.getSort()) {
			case Type.ARRAY:
			case Type.OBJECT:

				// Plain fields are taken care of already
				if (desc.equals("Ljava/lang/String;")
						|| Enumerations.get().contains(t.getClassName().replaceAll("/", "."))) {
					super.visitInsn(DUP);
					super.visitFieldInsn(opcode, owner, name + "__DEPENDENCY_INFO",
							Type.getDescriptor(DependencyInfo.class));
					super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(DependencyInfo.class), "read",
							"(Ljava/lang/Object;)V", false);
					super.visitFieldInsn(opcode, owner, name, desc);
				} else {
					// Note the order of visit ! We first call read to someone
					// else and then we call it on our stuff
					super.visitFieldInsn(opcode, owner, name, desc);
					// Why this one if we already have the one at the beginning
					// ~135
					// super.visitInsn(DUP);
					// super.visitMethodInsn(INVOKESTATIC,
					// Type.getInternalName(DependencyInfo.class), "read",
					// "(Ljava/lang/Object;)V", false);
				}
				break;
			case Type.BOOLEAN:
			case Type.BYTE:
			case Type.CHAR:
			case Type.INT:
			case Type.FLOAT:
			case Type.SHORT:
			case Type.DOUBLE:
			case Type.LONG:
				super.visitInsn(DUP);
				super.visitFieldInsn(opcode, owner, name + "__DEPENDENCY_INFO",
						Type.getDescriptor(DependencyInfo.class));
				super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(DependencyInfo.class), "read",
						"(Ljava/lang/Object;)V", false);
				super.visitFieldInsn(opcode, owner, name, desc);
				break;
			default:
				throw new UnsupportedOperationException();
			}

			break;
		// [] -> [value]
		case GETSTATIC:
			//
			// [] ->
			super.visitFieldInsn(GETSTATIC, owner, name, desc);
			// [ value ] ->

			if (!Instrumenter.isIgnoredClass(owner)) {

				// [ value ] ->
				super.visitFieldInsn(GETSTATIC, owner, name + "__DEPENDENCY_INFO",
						Type.getDescriptor(DependencyInfo.class));
				// [ value, value of taint ] ->
				Label ok = new Label();
				super.visitJumpInsn(IFNONNULL, ok);
				// [ value of name ] ->
				super.visitTypeInsn(NEW, Type.getInternalName(DependencyInfo.class));
				// // [ value of name, objectref taint ] ->
				super.visitInsn(DUP);
				// [ value of name, objectref taint, objectref taint] ->
				super.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(DependencyInfo.class), "<init>", "()V",
						false);
				// [ value of name, objectref taint, value taint ] ->
				super.visitFieldInsn(PUTSTATIC, owner, name + "__DEPENDENCY_INFO",
						Type.getDescriptor(DependencyInfo.class));
				// [ value of name, objectref taint ] ->

				// Not sure why this does not start only with value...
				// [] ->

				for (Pattern p : DependencyTrackingClassVisitor.fieldsLogged) {
					if (p.matcher(name).matches()) {
						// [] ->
						super.visitFieldInsn(GETSTATIC, owner, name + "__DEPENDENCY_INFO",
								Type.getDescriptor(DependencyInfo.class));
						// [new-value-dep-info (initialized) ] ->
						super.visitLdcInsn(name);
						// [new-value-dep-info (initialized), msg ] ->
						super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(DependencyInfo.class),
								"logMe", "(Ljava/lang/String;)V", false);
						// [] ->
					}
				}

				// Alessio: for togglz an.locals is null for a $$generated class
				if (an != null && an.locals != null) {
					Object[] locals = removeLongsDoubleTopVal(an.locals);
					Object[] stack = removeLongsDoubleTopVal(an.stack);
					super.visitLabel(ok);
					super.visitFrame(Opcodes.F_NEW, locals.length, locals, stack.length, stack);
				} else {
					super.visitLabel(ok);
				}

				if (isStaticInitializer) {
					// super.visitFieldInsn(GETSTATIC, "java/lang/System",
					// "out", "Ljava/io/PrintStream;");
					// super.visitLdcInsn(
					// "<clinit> Forbid read to " + owner.replaceAll("/", ".") +
					// "." + name + " in clinit");
					// super.visitMethodInsn(INVOKEVIRTUAL,
					// "java/io/PrintStream", "println",
					// "(Ljava/lang/String;)V",
					// false);

				} else {
					// [ value of name, objectref taint ] ->
					super.visitFieldInsn(GETSTATIC, owner, name + "__DEPENDENCY_INFO",
							Type.getDescriptor(DependencyInfo.class));
					// [ value of name, objectref taint, value of taint ] ->
					super.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(DependencyInfo.class), "read", "()V",
							false);
					// [ value of name, objectref taint] ->
				}

			} else {
				System.out.println("GETSTATIC IGNORE " + name);
			}
			break;

		// [ value ] -> []
		case PUTSTATIC: // All static fields inside a class are managed via an
						// external DEP_INFO data
			// [ value-of-field ] -> []
			super.visitFieldInsn(opcode, owner, name, desc);

			if (!Instrumenter.isIgnoredClass(owner)) {
				// []
				super.visitFieldInsn(GETSTATIC, owner, name + "__DEPENDENCY_INFO",
						Type.getDescriptor(DependencyInfo.class));
				// [ value-dep-info ]
				Label ok = new Label();
				super.visitJumpInsn(IFNONNULL, ok);
				// [] ->
				super.visitTypeInsn(NEW, Type.getInternalName(DependencyInfo.class));
				// [new-value-dep-info] ->
				super.visitInsn(DUP);
				// [new-value-dep-info, new-value-dep-info] ->
				// objectref, [arg1, [arg2 ...]] ->
				super.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(DependencyInfo.class), "<init>", "()V",
						false);
				// [new-value-dep-info (initialized) ] ->
				super.visitFieldInsn(PUTSTATIC, owner, name + "__DEPENDENCY_INFO",
						Type.getDescriptor(DependencyInfo.class));
				// [] ->
				for (Pattern p : DependencyTrackingClassVisitor.fieldsLogged) {
					if (p.matcher(name).matches()) {
						// [] ->
						super.visitFieldInsn(GETSTATIC, owner, name + "__DEPENDENCY_INFO",
								Type.getDescriptor(DependencyInfo.class));
						// [new-value-dep-info (initialized) ] ->
						super.visitLdcInsn(name);
						// [new-value-dep-info (initialized), msg ] ->
						super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(DependencyInfo.class),
								"logMe", "(Ljava/lang/String;)V", false);
						// [] ->
					}
				}

				// No idea what's this... but this close the if
				if (an != null) {
					Object[] locals = removeLongsDoubleTopVal(an.locals);
					Object[] stack = removeLongsDoubleTopVal(an.stack);
					super.visitLabel(ok);
					super.visitFrame(Opcodes.F_NEW, locals.length, locals, stack.length, stack);
				} else {
					super.visitLabel(ok);
				}

				if (isStaticInitializer) {
					// super.visitFieldInsn(GETSTATIC, "java/lang/System",
					// "out", "Ljava/io/PrintStream;");
					// super.visitLdcInsn(
					// "<clinit> Forbid write to " + owner.replaceAll("/", ".")
					// + "." + name + " in clinit");
					// super.visitMethodInsn(INVOKEVIRTUAL,
					// "java/io/PrintStream", "println",
					// "(Ljava/lang/String;)V",
					// false);

				} else {
					//
					// super.visitFieldInsn(GETSTATIC, "java/lang/System",
					// "out", "Ljava/io/PrintStream;");
					// super.visitLdcInsn(
					// "<clinit> Calling write to " + owner.replaceAll("/", ".")
					// + "." + name + " in clinit");
					// super.visitMethodInsn(INVOKEVIRTUAL,
					// "java/io/PrintStream", "println",
					// "(Ljava/lang/String;)V",
					// false);
					//
					// [] ->
					super.visitFieldInsn(GETSTATIC, owner, name + "__DEPENDENCY_INFO",
							Type.getDescriptor(DependencyInfo.class));
					// [ value of taint ] ->
					super.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(DependencyInfo.class), "write", "()V",
							false);
					// [ ] ->
				}

			} else {
				System.out.println("PUTSTATIC IGNORE " + name);
			}
			break;
		case PUTFIELD:
			t = Type.getType(desc);
			if (inUninitializedSuper) {
				// At this point __initPrimDepInfo() is already called
				super.visitFieldInsn(opcode, owner, name, desc);
				return;
			}

			if (isStaticInitializer) {
				// super.visitFieldInsn(PUTFIELD, "java/lang/System", "out",
				// "Ljava/io/PrintStream;");
				// super.visitLdcInsn(
				// "<clinit> Forbid write to " + owner.replaceAll("/", ".") +
				// "." + name + " in clinit");
				// super.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream",
				// "println", "(Ljava/lang/String;)V",
				// false);

				super.visitFieldInsn(opcode, owner, name, desc);
				return;
			}

			if (t.getSort() == Type.ARRAY || t.getSort() == Type.OBJECT) {

				if (Enumerations.get().contains(t.getClassName().replaceAll("/", "."))
						|| String.class.getName().equals(t.getClassName().replaceAll("/", ".")) //
				) {

					// There are two cases, the null is in the initialization ->
					// we do not care about it
					// the null was explicitly set -> we care about that

					if ("<init>".equals(methodName)) {
						// System.out
						// .println("RWTrackingMethodVisitor.visitFieldInsn()
						// Process NULL During initialization");
						// [objectref, value]
						super.visitInsn(DUP);
						// [objectref, value, value]
						Label l1 = new Label();
						super.visitJumpInsn(IFNULL, l1);
						// IF BRANCH == NOT NULL >> Value is not null
						// [objectref, value]
						super.visitInsn(SWAP);
						//
						//
						// super.visitFieldInsn(GETSTATIC, "java/lang/System",
						// "out", "Ljava/io/PrintStream;");
						// super.visitLdcInsn(
						// "<clinit> Calling write to " + owner.replaceAll("/",
						// ".") + "." + name + " in init");
						// super.visitMethodInsn(INVOKEVIRTUAL,
						// "java/io/PrintStream", "println",
						// "(Ljava/lang/String;)V",
						// false);
						//
						//
						// [value, objectref]
						super.visitInsn(DUP);
						// [value, objectref, objectref]
						super.visitFieldInsn(Opcodes.GETFIELD, owner, name + "__DEPENDENCY_INFO",
								Type.getDescriptor(DependencyInfo.class));
						// [value, objectref, value2 ]
						super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(DependencyInfo.class), "write",
								"(Ljava/lang/Object;)V", false);
						// [value, objectref ]
						super.visitInsn(SWAP);
						// [value, objectref ]
						// ELSE BRANCH --> OBJECT IS NULL ?! Do nothing
						super.visitLabel(l1);

						// [objectref, value ] -->
						super.visitFieldInsn(opcode, owner, name, desc);
						// []

					} else { // This propagates the write to objects despite
								// their value
						// [objectref, value]
						super.visitInsn(SWAP);
						//
						//
						// super.visitFieldInsn(GETSTATIC, "java/lang/System",
						// "out", "Ljava/io/PrintStream;");
						// super.visitLdcInsn("<clinit> Calling write to " +
						// owner.replaceAll("/", ".") + "." + name);
						// super.visitMethodInsn(INVOKEVIRTUAL,
						// "java/io/PrintStream", "println",
						// "(Ljava/lang/String;)V",
						// false);
						//
						//
						// [value, objectref]
						super.visitInsn(DUP);
						// [value, objectref, objectref]
						super.visitFieldInsn(Opcodes.GETFIELD, owner, name + "__DEPENDENCY_INFO",
								Type.getDescriptor(DependencyInfo.class));
						// [value, objectref, value-of-dep ]
						super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(DependencyInfo.class), "write",
								"(Ljava/lang/Object;)V", false);
						// [value, objectref ]
						super.visitInsn(SWAP);
						// [objectref, value ] -->
						super.visitFieldInsn(opcode, owner, name, desc);
						// []
					}

				} else {

					// [objectref, value ] -->
					super.visitInsn(SWAP);
					// [value, objectref] -->
					super.visitInsn(DUP);
					// [value, objectref, objectref] -->
					super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(DependencyInfo.class), "write",
							"(Ljava/lang/Object;)V", false);
					// [value, objectref ] -->
					super.visitInsn(SWAP);
					// [objectref, value ] -->
					super.visitFieldInsn(opcode, owner, name, desc);
					// []
				}
			} else {
				switch (t.getSize()) {
				case 1:
					super.visitInsn(SWAP);
					//
					//
					// super.visitFieldInsn(GETSTATIC, "java/lang/System",
					// "out", "Ljava/io/PrintStream;");
					// super.visitLdcInsn("<clinit> Calling write to " +
					// owner.replaceAll("/", ".") + "." + name);
					// super.visitMethodInsn(INVOKEVIRTUAL,
					// "java/io/PrintStream", "println",
					// "(Ljava/lang/String;)V",
					// false);
					//
					///
					super.visitInsn(DUP);
					super.visitFieldInsn(Opcodes.GETFIELD, owner, name + "__DEPENDENCY_INFO",
							Type.getDescriptor(DependencyInfo.class));
					super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(DependencyInfo.class), "write",
							"(Ljava/lang/Object;)V", false);
					super.visitInsn(SWAP);
					super.visitFieldInsn(opcode, owner, name, desc);
					break;
				case 2:
					//
					//
					// super.visitFieldInsn(GETSTATIC, "java/lang/System",
					// "out", "Ljava/io/PrintStream;");
					// super.visitLdcInsn("<clinit> Calling write to " +
					// owner.replaceAll("/", ".") + "." + name);
					// super.visitMethodInsn(INVOKEVIRTUAL,
					// "java/io/PrintStream", "println",
					// "(Ljava/lang/String;)V",
					// false);
					//
					///
					super.visitInsn(DUP2_X1);
					super.visitInsn(POP2);
					super.visitInsn(DUP);
					super.visitInsn(POP);
					// super.visitMethodInsn(INVOKESTATIC,
					// Type.getInternalName(DependencyInfo.class), "write",
					// "(Ljava/lang/Object;)V", false);
					super.visitInsn(DUP);
					super.visitFieldInsn(Opcodes.GETFIELD, owner, name + "__DEPENDENCY_INFO",
							Type.getDescriptor(DependencyInfo.class));
					super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(DependencyInfo.class), "write",
							"(Ljava/lang/Object;)V", false);
					super.visitInsn(DUP_X2);
					super.visitInsn(POP);

					super.visitFieldInsn(opcode, owner, name, desc);
					break;
				}
			}
			break;
		}
	}

	// For togglz project this results in a NPE.
	protected static Object[] removeLongsDoubleTopVal(List<Object> in) {

		ArrayList<Object> ret = new ArrayList<Object>();

		if (in != null) {
			boolean lastWas2Word = false;
			for (Object n : in) {
				if (n == Opcodes.TOP && lastWas2Word) {
					// nop
				} else
					ret.add(n);
				if (n == Opcodes.DOUBLE || n == Opcodes.LONG)
					lastWas2Word = true;
				else
					lastWas2Word = false;
			}
		}
		return ret.toArray();
	}

	LocalVariablesSorter lvs;

	public void setLVS(LocalVariablesSorter lvs) {
		this.lvs = lvs;
	}

	AnalyzerAdapter an;

	public void setAnalyzer(AnalyzerAdapter an) {
		this.an = an;
	}

}