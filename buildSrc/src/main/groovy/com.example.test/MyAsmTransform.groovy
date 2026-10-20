package com.example.test

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

/**
 * Chapter 3.3.1 & Chapter 7.6.2: Modern AGP 8+ AsmClassVisitorFactory implementation.
 */
abstract class MyAsmTransform implements AsmClassVisitorFactory<InstrumentationParameters.None> {

    @Override
    ClassVisitor createClassVisitor(
            ClassContext classContext,
            ClassVisitor nextClassVisitor) {
        return new TestClassVisitor(Opcodes.ASM9, nextClassVisitor, classContext.currentClassData.className)
    }

    @Override
    boolean isInstrumentable(ClassData classData) {
        return classData.className.startsWith("com.example.performance_optimize")
    }
}