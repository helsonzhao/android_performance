package com.example.performance_optimize.stability;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Debug;
import android.util.Log;
import android.widget.Toast;

import com.bytedance.android.bytehook.ByteHook;
import com.example.performance_optimize.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Chapter 7: Practical Stability Optimization Demo Activity.
 * Covers:
 * - 7.1 Native Crash Monitoring (sigaltstack + sigaction & Google Breakpad)
 * - 7.2 ANR Monitoring (SIGQUIT interception, ActivityManager secondary check, libc write hook)
 * - 7.3 OOM Monitoring & On-Device HPROF Tailoring (Debug.dumpHprofData + HprofStripper)
 * - 7.6 Slow Function Monitoring (SlowMethodMonitor)
 */
public class StabilityExampleActivity extends AppCompatActivity {
    private static final String TAG = "StabilityExample";

    static {
        System.loadLibrary("example");
        System.loadLibrary("optimize");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stability_example);
        ByteHook.init();
        installCustomSignalHandlers();

        findViewById(R.id.anr).setOnClickListener(v -> {
            hookAnrByBHook();
            Thread contender = new Thread(() -> {
                synchronized (StabilityExampleActivity.this) {
                    try {
                        Thread.sleep(6000);
                    } catch (InterruptedException ignored) {
                    }
                }
            }, "AnrLockContender");
            contender.start();
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
            }
            Log.i(TAG, "Main thread waiting on contended monitor lock...");
            synchronized (StabilityExampleActivity.this) {
                Log.i(TAG, "Main thread acquired lock; checking ActivityManager ANR state: " + isProcessInAnrState());
            }
        });

        findViewById(R.id.oom).setOnClickListener(v -> {
            try {
                File rawHprof = new File(getFilesDir(), "raw_dump.hprof");
                File tailoredHprof = new File(getFilesDir(), "tailored_dump.hprof");
                Debug.dumpHprofData(rawHprof.getAbsolutePath());
                long tailoredBytes = HprofStripper.stripPrimitiveArrays(rawHprof, tailoredHprof);
                String msg = "Dumped & processed HPROF: " + tailoredBytes + " bytes";
                Log.i(TAG, msg);
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Failed to dump/strip HPROF", e);
            }
        });

        findViewById(R.id.crash).setOnClickListener(v -> {
            ArrayList<String> testList = null;
            testList.size();
        });

        findViewById(R.id.native_crash).setOnClickListener(v -> mockCrash());

        findViewById(R.id.breakpad).setOnClickListener(v -> captureNativeCrash());

        findViewById(R.id.slow_method).setOnClickListener(v -> {
            long token = SlowMethodMonitor.onMethodEnter("StabilityExampleActivity.simulateSlowMainThreadWork");
            try {
                Thread.sleep(180);
            } catch (InterruptedException ignored) {
            } finally {
                SlowMethodMonitor.onMethodExit("StabilityExampleActivity.simulateSlowMainThreadWork", token);
            }
            Toast.makeText(this, "Simulated 180ms slow main-thread method (check Logcat)", Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Chapter 7.2.2: Secondary confirmation via ActivityManager.getProcessesInErrorState().
     */
    private boolean isProcessInAnrState() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        List<ActivityManager.ProcessErrorStateInfo> errorList = am.getProcessesInErrorState();
        if (errorList == null) return false;
        for (ActivityManager.ProcessErrorStateInfo info : errorList) {
            if (info.pid == android.os.Process.myPid() &&
                    info.condition == ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING) {
                return true;
            }
        }
        return false;
    }

    private static native void mockCrash();

    private static native void captureNativeCrash();

    private static native void hookAnrByBHook();

    private native void installCustomSignalHandlers();
}