package com.example.performance_optimize.speed;

import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Chapter 5.8: Unified Thread Pool Optimization and Governance.
 * Separates CPU-bound and I/O-bound workloads into dedicated bounded pools and
 * instruments beforeExecute / afterExecute to detect slow or deadlocked tasks.
 */
public final class MonitoredThreadPool extends ThreadPoolExecutor {
    private static final String TAG = "MonitoredThreadPool";
    private static final long SLOW_TASK_THRESHOLD_MS = 2000L;

    private final String poolName;
    private final Map<Runnable, Long> startTimes = new ConcurrentHashMap<>();

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    private static final MonitoredThreadPool CPU_POOL = new MonitoredThreadPool(
            "CpuBoundPool",
            Math.max(2, CPU_COUNT),
            Math.max(2, CPU_COUNT),
            30L,
            128);

    private static final MonitoredThreadPool IO_POOL = new MonitoredThreadPool(
            "IoBoundPool",
            Math.max(4, CPU_COUNT * 2),
            Math.max(8, CPU_COUNT * 4),
            60L,
            256);

    public static MonitoredThreadPool cpuPool() {
        return CPU_POOL;
    }

    public static MonitoredThreadPool ioPool() {
        return IO_POOL;
    }

    public MonitoredThreadPool(
            final String poolName,
            int corePoolSize,
            int maximumPoolSize,
            long keepAliveSeconds,
            int queueCapacity) {
        super(
                corePoolSize,
                maximumPoolSize,
                keepAliveSeconds,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new ThreadFactory() {
                    private final AtomicInteger count = new AtomicInteger(1);

                    @Override
                    public Thread newThread(final Runnable r) {
                        return new Thread(() -> {
                            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                            r.run();
                        }, poolName + "-Worker-" + count.getAndIncrement());
                    }
                },
                new CallerRunsPolicy());
        this.poolName = poolName;
        allowCoreThreadTimeOut(true);
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
        startTimes.put(r, SystemClock.elapsedRealtime());
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        try {
            Long start = startTimes.remove(r);
            if (start != null) {
                long durationMs = SystemClock.elapsedRealtime() - start;
                if (durationMs >= SLOW_TASK_THRESHOLD_MS) {
                    Log.w(TAG, String.format(
                            "[%s] Slow task detected (%d ms): %s",
                            poolName, durationMs, r.getClass().getName()));
                } else {
                    Log.i(TAG, String.format(
                            "[%s] Task completed in %d ms on %s",
                            poolName, durationMs, Thread.currentThread().getName()));
                }
            }
            if (t != null) {
                Log.e(TAG, "[" + poolName + "] Uncaught task exception", t);
            }
        } finally {
            super.afterExecute(r, t);
        }
    }
}
