package com.example.performance_optimize.aop;

import android.util.Log;

import com.example.performance_optimize.speed.MonitoredThreadPool;
import com.flyjingfish.android_aop_annotation.anno.AndroidAopReplaceClass;
import com.flyjingfish.android_aop_annotation.anno.AndroidAopReplaceMethod;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Chapter 6.3.2: Redirects ad-hoc Executors.newFixedThreadPool(int) calls at compile time
 * to a unified MonitoredThreadPool instance with task queuing & execution latency telemetry.
 */
@AndroidAopReplaceClass("java.util.concurrent.Executors")
public final class ExecutorsGovernanceHook {
    private static final String TAG = "ExecutorsGovernanceHook";

    private ExecutorsGovernanceHook() {
    }

    @AndroidAopReplaceMethod("java.util.concurrent.ExecutorService newFixedThreadPool(int)")
    public static ExecutorService newFixedThreadPool(int nThreads) {
        Log.i(TAG, "Redirecting Executors.newFixedThreadPool(" + nThreads + ") to MonitoredThreadPool");
        return new MonitoredThreadPool(
                "GovernedFixedPool",
                nThreads,
                nThreads,
                60L,
                128);
    }
}
