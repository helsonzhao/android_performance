package com.example.test

import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Chapter 3.3.1 & Chapter 7.6.2: Modern AGP 8+ Bytecode Instrumentation Plugin
 * using AndroidComponentsExtension and AsmClassVisitorFactory.
 */
class MyAsmPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        AndroidComponentsExtension androidComponents =
                project.extensions.getByType(AndroidComponentsExtension.class)

        androidComponents.onVariants(androidComponents.selector().all()) { variant ->
            variant.instrumentation.transformClassesWith(
                    MyAsmTransform.class,
                    InstrumentationScope.ALL
            ) {}
            variant.instrumentation.setAsmFramesComputationMode(
                    FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
            )
        }
    }
}