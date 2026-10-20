package com.example.performance_optimize.aop;

import android.util.Log;

import com.flyjingfish.android_aop_annotation.anno.AndroidAopReplaceClass;
import com.flyjingfish.android_aop_annotation.anno.AndroidAopReplaceMethod;

/**
 * Chapter 3.4.2: Intercepts Thread(Runnable) instantiation at compile time using
 * FlyJingFish/AndroidAOP to log thread creation stack traces for thread governance.
 */
@AndroidAopReplaceClass("java.lang.Thread")
public final class ThreadCreationHook {
    private static final String TAG = "ThreadCreationHook";

    private ThreadCreationHook() {
    }

    @AndroidAopReplaceMethod("<init>(java.lang.Runnable)")
    public static Thread newThread(Runnable target, Class<?> clazz) {
        Thread thread = new Thread(target);
        Log.d(TAG, "Thread created: name=" + thread.getName(),
                new Throwable("Thread creation call stack"));
        return thread;
    }
}
