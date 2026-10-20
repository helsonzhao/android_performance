package com.example.test

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.AdviceAdapter

/**
 * Chapter 3.3.1 & Chapter 7.6.2: ASM ClassVisitor and AdviceAdapter that instruments
 * method entry and exit for diagnostics and main-thread slow method detection.
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
        if (mv == null || name == "<init>" || name == "<clinit>") {
            return mv
        }
        return new TestMethodVisitor(api, mv, access, name, desc, "${className}.${name}")
    }

    private static class TestMethodVisitor extends AdviceAdapter {
        private final String methodSignature

        protected TestMethodVisitor(int api, MethodVisitor mv, int access, String name, String desc, String methodSignature) {
            super(api, mv, access, name, desc)
            this.methodSignature = methodSignature
        }

        @Override
        protected void onMethodEnter() {
            // Print method entry trace via System.out.print
            mv.visitFieldInsn(Opcodes.GETSTATIC,
                    "java/lang/System", "out", "Ljava/io/PrintStream;")
            mv.visitLdcInsn("[ASM Trace] Entering " + methodSignature)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false)
            super.onMethodEnter()
        }
    }
}