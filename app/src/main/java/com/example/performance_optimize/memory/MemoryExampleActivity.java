package com.example.performance_optimize.memory;

import androidx.fragment.app.FragmentActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.bytedance.android.bytehook.ByteHook;
import com.example.performance_optimize.R;

/**
 * Chapter 3: Practical Memory Optimization Demo Activity.
 * Covers:
 * - 3.1 Java Memory Leak Detection
 * - 3.2 Native Memory Leak Detection (PLT/GOT & ByteHook)
 * - 3.3 Bitmap Governance (Oversized Bitmap Detection & Downsampling)
 * - 3.4 Thread Stack Optimization (pthread_attr_setstacksize Hook)
 * - 3.5 Releasing Default WebView Reserved Memory ([anon:libwebview reservation])
 */
public class MemoryExampleActivity extends FragmentActivity {
    private static final String TAG = "MemoryExampleActivity";

    static {
        System.loadLibrary("optimize");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_memory_example);
        ByteHook.init();

        findViewById(R.id.java_leak).setOnClickListener(
                v -> startActivity(new Intent(MemoryExampleActivity.this, JavaLeakActivity.class)));

        findViewById(R.id.native_leak).setOnClickListener(
                v -> startActivity(new Intent(MemoryExampleActivity.this, NativeLeakActivity.class)));

        findViewById(R.id.bitmap_governance).setOnClickListener(v -> {
            Bitmap testCreated = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
            Bitmap sampled = BitmapGovernanceHelper.decodeSampledBitmapFromResource(
                    getResources(), R.mipmap.ic_launcher, 48, 48);
            int bytes = (sampled != null ? sampled.getAllocationByteCount() : 0)
                    + (testCreated != null ? testCreated.getAllocationByteCount() : 0);
            Log.i(TAG, "Decoded downsampled RGB_565 Bitmap: " + bytes + " bytes");
            Toast.makeText(this, "Downsampled Bitmap allocated: " + bytes + " bytes", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.shrink_thread_stack).setOnClickListener(v -> {
            shrinkThreadStack();
            new Thread(() -> Log.i(TAG, "Worker thread running with 512 KB shrunk stack"), "ShrunkStackWorker").start();
            Toast.makeText(this, "Hooked pthread_attr_setstacksize (1MB -> 512KB)", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.release_webview_reserve).setOnClickListener(v -> {
            long released = releaseWebViewReservation();
            Log.i(TAG, "Released [anon:libwebview reservation] bytes: " + released);
            Toast.makeText(this, "Released WebView reserved VM: " + released + " bytes", Toast.LENGTH_SHORT).show();
        });
    }

    private native void shrinkThreadStack();

    private native long releaseWebViewReservation();
}