package com.example.test

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.AdviceAdapter

/**
 * Chapter 3.3.1 & Chapter 7.6.2: ASM ClassVisitor and AdviceAdapter that instruments
 * method entry/exit for diagnostics and main-thread slow method detection.
 */
class TestClassVisitor extends ClassVisitor {
    private final String className

    TestClassVisitor(int api, ClassVisitor cv, String className = "") {
        super(api, cv)
        this.className = className
    }

    @Override
    MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions)
        if (mv == null || name == "<init>" || name == "<clinit>" ||
                className.endsWith("SlowMethodMonitor")) {
            return mv
        }
        return new TestMethodVisitor(api, mv, access, name, desc, "${className}.${name}")
    }

    private static class TestMethodVisitor extends AdviceAdapter {
        private final String methodSignature
        private int startTimeLocal = -1

        protected TestMethodVisitor(int api, MethodVisitor mv, int access, String name, String desc, String methodSignature) {
            super(api, mv, access, name, desc)
            this.methodSignature = methodSignature
        }

        @Override
        protected void onMethodEnter() {
            // Chapter 3.3.1: Print method entry trace
            mv.visitFieldInsn(Opcodes.GETSTATIC,
                    "java/lang/System", "out", "Ljava/io/PrintStream;")
            mv.visitLdcInsn("[ASM Trace] Entering " + methodSignature)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false)

            // Chapter 7.6.2: Record main-thread method entry timestamp via SlowMethodMonitor
            startTimeLocal = newLocal(Type.LONG_TYPE)
            mv.visitLdcInsn(methodSignature)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                    "com/example/performance_optimize/stability/SlowMethodMonitor",
                    "onMethodEnter",
                    "(Ljava/lang/String;)J",
                    false)
            mv.visitVarInsn(Opcodes.LSTORE, startTimeLocal)
            super.onMethodEnter()
        }

        @Override
        protected void onMethodExit(int opcode) {
            if (startTimeLocal >= 0) {
                mv.visitLdcInsn(methodSignature)
                mv.visitVarInsn(Opcodes.LLOAD, startTimeLocal)
                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "com/example/performance_optimize/stability/SlowMethodMonitor",
                        "onMethodExit",
                        "(Ljava/lang/String;J)V",
                        false)
            }
            super.onMethodExit(opcode)
        }
    }
}