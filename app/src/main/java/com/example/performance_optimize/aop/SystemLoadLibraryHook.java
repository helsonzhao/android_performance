package com.example.performance_optimize.aop;

import android.util.Log;

import com.flyjingfish.android_aop_annotation.anno.AndroidAopReplaceClass;
import com.flyjingfish.android_aop_annotation.anno.AndroidAopReplaceMethod;

/**
 * Chapter 9.8.2: Intercepts System.loadLibrary(String) at compile time using
 * FlyJingFish/AndroidAOP to allow fallback to dynamically downloaded native libraries.
 */
@AndroidAopReplaceClass("java.lang.System")
public final class SystemLoadLibraryHook {
    private static final String TAG = "SystemLoadLibraryHook";

    private SystemLoadLibraryHook() {
    }

    @AndroidAopReplaceMethod("void loadLibrary(java.lang.String)")
    public static void loadLibrary(String libName) {
        Log.d(TAG, "Intercepted System.loadLibrary(\"" + libName + "\")");
        System.loadLibrary(libName);
    }
}
