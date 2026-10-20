package com.example.performance_optimize.other;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Chapter 10.4.4: Reference Implementation of the Dynamic Graceful Degradation Framework.
 * Collects device health telemetry (battery temperature, Java heap utilization, UI jank ratio),
 * computes a composite anomaly score, and dispatches prioritized degradation / restoration tasks.
 */
public final class DevicePerfManager {
    private static final String TAG = "DevicePerfManager";

    public enum PerfLevel {
        EXCELLENT,
        NORMAL,
        DEGRADED,
        CRITICAL
    }

    public static abstract class DegradationTask implements Comparable<DegradationTask> {
        private final String name;
        private final int priority; // Lower value = higher priority (degraded first)
        private boolean isDegraded = false;

        protected DegradationTask(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }

        public String getName() {
            return name;
        }

        public boolean isDegraded() {
            return isDegraded;
        }

        void applyDegrade(PerfLevel level) {
            if (!isDegraded) {
                isDegraded = true;
                onDegrade(level);
            }
        }

        void applyRestore() {
            if (isDegraded) {
                isDegraded = false;
                onRestore();
            }
        }

        protected abstract void onDegrade(PerfLevel level);

        protected abstract void onRestore();

        @Override
        public int compareTo(DegradationTask other) {
            return Integer.compare(this.priority, other.priority);
        }
    }

    private static final DevicePerfManager INSTANCE = new DevicePerfManager();
    private final List<DegradationTask> tasks = new ArrayList<>();
    private PerfLevel currentLevel = PerfLevel.NORMAL;

    private DevicePerfManager() {
    }

    public static DevicePerfManager getInstance() {
        return INSTANCE;
    }

    public synchronized void registerTask(DegradationTask task) {
        tasks.add(task);
        Collections.sort(tasks);
    }

    public synchronized PerfLevel evaluateAndDispatch(Context context, float jankFrameRatio) {
        float batteryTempC = readBatteryTemperatureC(context);
        Runtime rt = Runtime.getRuntime();
        long usedMem = rt.totalMemory() - rt.freeMemory();
        float heapRatio = (float) usedMem / Math.max(1L, rt.maxMemory());

        int score = 100;
        if (batteryTempC >= 43.0f) {
            score -= 35;
        } else if (batteryTempC >= 39.0f) {
            score -= 15;
        }
        if (heapRatio >= 0.85f) {
            score -= 35;
        } else if (heapRatio >= 0.70f) {
            score -= 15;
        }
        if (jankFrameRatio >= 0.15f) {
            score -= 30;
        } else if (jankFrameRatio >= 0.05f) {
            score -= 15;
        }

        if (score >= 85) {
            currentLevel = PerfLevel.EXCELLENT;
        } else if (score >= 65) {
            currentLevel = PerfLevel.NORMAL;
        } else if (score >= 40) {
            currentLevel = PerfLevel.DEGRADED;
        } else {
            currentLevel = PerfLevel.CRITICAL;
        }

        Log.i(TAG, String.format(
                "Evaluated device health: temp=%.1fC, heap=%.1f%%, jank=%.1f%% -> score=%d (%s)",
                batteryTempC, heapRatio * 100f, jankFrameRatio * 100f, score, currentLevel));

        dispatchTasks(currentLevel);
        return currentLevel;
    }

    private void dispatchTasks(PerfLevel level) {
        if (level == PerfLevel.EXCELLENT || level == PerfLevel.NORMAL) {
            for (DegradationTask task : tasks) {
                task.applyRestore();
            }
        } else if (level == PerfLevel.DEGRADED) {
            // Degrade top-half highest-priority tasks
            int half = Math.max(1, tasks.size() / 2);
            for (int i = 0; i < tasks.size(); i++) {
                if (i < half) {
                    tasks.get(i).applyDegrade(level);
                } else {
                    tasks.get(i).applyRestore();
                }
            }
        } else {
            // CRITICAL: Degrade all registered tasks
            for (DegradationTask task : tasks) {
                task.applyDegrade(level);
            }
        }
    }

    private float readBatteryTemperatureC(Context context) {
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) {
            return 32.0f;
        }
        int tenthsOfDegree = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 320);
        return tenthsOfDegree / 10.0f;
    }
}
