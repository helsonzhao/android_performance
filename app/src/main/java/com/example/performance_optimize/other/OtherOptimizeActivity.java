package com.example.performance_optimize.other;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.TrafficStats;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.performance_optimize.R;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Chapter 10: Other Optimizations (Power, Network Traffic, Disk Footprint, Graceful Degradation).
 */
public class OtherOptimizeActivity extends AppCompatActivity {
    private static final String TAG = "OtherOptimizeActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_other_optimize);

        // Register sample degradation tasks (Chapter 10.4.2)
        DevicePerfManager.getInstance().registerTask(new DevicePerfManager.DegradationTask("DisableParticleEffects", 1) {
            @Override
            protected void onDegrade(DevicePerfManager.PerfLevel level) {
                Log.w(TAG, "[Degraded] Disabled complex particle animations for level=" + level);
            }

            @Override
            protected void onRestore() {
                Log.i(TAG, "[Restored] Re-enabled particle animations");
            }
        });
        DevicePerfManager.getInstance().registerTask(new DevicePerfManager.DegradationTask("LowerImageDecodeQuality", 2) {
            @Override
            protected void onDegrade(DevicePerfManager.PerfLevel level) {
                Log.w(TAG, "[Degraded] Switched image decoder to RGB_565 for level=" + level);
            }

            @Override
            protected void onRestore() {
                Log.i(TAG, "[Restored] Restored image decoder to ARGB_8888");
            }
        });

        findViewById(R.id.power_profile_check).setOnClickListener(v -> {
            double batteryCapacityMah = readBatteryCapacityMah(this);
            String msg = "PowerProfile battery capacity: " + batteryCapacityMah + " mAh";
            Log.i(TAG, msg);
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.traffic_stats_check).setOnClickListener(v -> {
            int uid = Process.myUid();
            long rxBytes = TrafficStats.getUidRxBytes(uid);
            long txBytes = TrafficStats.getUidTxBytes(uid);
            String msg = "UID Traffic — RX: " + rxBytes + " bytes, TX: " + txBytes + " bytes";
            Log.i(TAG, msg);
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.disk_space_scan).setOnClickListener(v -> {
            long internalBytes = calculateDirectorySize(getDataDir());
            String msg = "App sandbox disk usage: " + internalBytes + " bytes";
            Log.i(TAG, msg);
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.degradation_eval).setOnClickListener(v -> {
            DevicePerfManager.PerfLevel level =
                    DevicePerfManager.getInstance().evaluateAndDispatch(this, 0.18f);
            Toast.makeText(this, "Evaluated PerfLevel: " + level, Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Chapter 10.1.1: Reads hardware power constants from com.android.internal.os.PowerProfile.
     */
    @SuppressLint("PrivateApi")
    private double readBatteryCapacityMah(Context context) {
        try {
            Class<?> powerProfileClass = Class.forName("com.android.internal.os.PowerProfile");
            Constructor<?> constructor = powerProfileClass.getConstructor(Context.class);
            Object powerProfile = constructor.newInstance(context);
            Method getBatteryCapacity = powerProfileClass.getMethod("getBatteryCapacity");
            return (double) getBatteryCapacity.invoke(powerProfile);
        } catch (Exception e) {
            Log.w(TAG, "PowerProfile reflection fallback", e);
            return 4000.0;
        }
    }

    /**
     * Chapter 10.3.1: Recursively calculates total disk usage of an application directory.
     */
    private long calculateDirectorySize(File dir) {
        if (dir == null || !dir.exists()) {
            return 0L;
        }
        if (dir.isFile()) {
            return dir.length();
        }
        long total = 0L;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                total += calculateDirectorySize(child);
            }
        }
        return total;
    }
}
