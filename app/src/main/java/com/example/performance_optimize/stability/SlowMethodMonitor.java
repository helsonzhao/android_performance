package com.example.performance_optimize.stability;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

/**
 * Chapter 7.6: Slow Function Monitoring on the Main Thread.
 * Schedules a background stack-trace sampler whenever a main-thread method takes longer
 * than {@link #SLOW_METHOD_THRESHOLD_MS} to complete.
 */
public final class SlowMethodMonitor {
    private static final String TAG = "SlowMethodMonitor";
    private static final long SLOW_METHOD_THRESHOLD_MS = 100L;

    private static final HandlerThread WATCHDOG_THREAD = new HandlerThread("SlowMethodWatchdog");
    private static final Handler WATCHDOG_HANDLER;

    static {
        WATCHDOG_THREAD.start();
        WATCHDOG_HANDLER = new Handler(WATCHDOG_THREAD.getLooper());
    }

    private SlowMethodMonitor() {
    }

    public static long onMethodEnter(final String methodSignature) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return -1L;
        }
        return SystemClock.elapsedRealtime();
    }

    public static void onMethodExit(final String methodSignature, final long enterTimestampMs) {
        if (enterTimestampMs < 0 || Looper.myLooper() != Looper.getMainLooper()) {
            return;
        }
        long durationMs = SystemClock.elapsedRealtime() - enterTimestampMs;
        if (durationMs >= SLOW_METHOD_THRESHOLD_MS) {
            final StackTraceElement[] stack = Looper.getMainLooper().getThread().getStackTrace();
            WATCHDOG_HANDLER.post(() -> {
                StringBuilder sb = new StringBuilder();
                sb.append("Slow main-thread method: ")
                        .append(methodSignature)
                        .append(" took ")
                        .append(durationMs)
                        .append(" ms\n");
                for (StackTraceElement element : stack) {
                    sb.append("    at ").append(element.toString()).append("\n");
                }
                Log.w(TAG, sb.toString());
            });
        }
    }
}
