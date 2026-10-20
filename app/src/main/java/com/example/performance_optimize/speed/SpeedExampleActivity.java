package com.example.performance_optimize.speed;

import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bytedance.android.bytehook.ByteHook;
import com.example.performance_optimize.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

/**
 * Chapter 5: Practical Optimization of Speed and Fluency Demo Activity.
 * Covers:
 * - 5.1 Utilizing CPU Idle Windows (POSIX times() & /proc/<pid>/stat)
 * - 5.2 Reducing CPU Wait States (Zero-Copy mmap I/O)
 * - 5.3 Binding Critical Threads to Big CPU Cores (sched_setaffinity)
 * - 5.4 Garbage Collection (GC) Suppression Window (HeapTaskDaemon Hook)
 * - 5.5 Cache Eviction Strategy Optimization (O(1) LFUCache)
 * - 5.7 Elevating Core Thread Priorities (Main Thread & RenderThread)
 * - 5.8 Unified Thread Pool Optimization and Governance (MonitoredThreadPool)
 */
public class SpeedExampleActivity extends AppCompatActivity {
    private static final String TAG = "SpeedExampleActivity";

    static {
        System.loadLibrary("optimize");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speed_example);
        ByteHook.init();

        findViewById(R.id.cpu_idle_check).setOnClickListener(v -> {
            long[] ticks = getProcessCpuTicks();
            String msg = "Wall ticks=" + ticks[0] + ", Process CPU ticks=" + ticks[1] + " (CLK_TCK=" + ticks[2] + ")";
            Log.i(TAG, msg);
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.mmap_io_demo).setOnClickListener(v -> {
            File target = new File(getFilesDir(), "perf_mmap_log.bin");
            try (RandomAccessFile raf = new RandomAccessFile(target, "rw");
                 FileChannel channel = raf.getChannel()) {
                byte[] payload = "Zero-copy page-cache write via MappedByteBuffer\n".getBytes(StandardCharsets.UTF_8);
                MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, payload.length);
                buffer.put(payload);
                Toast.makeText(this, "Wrote " + payload.length + "B via mmap()", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "mmap write failed", e);
            }
        });

        findViewById(R.id.bind_big_core).setOnClickListener(v -> {
            boolean bound = bindCurrentThreadToBigCores();
            Toast.makeText(this, "Bound main thread to Big CPU Cores: " + bound, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.gc_suppression).setOnClickListener(v -> {
            enableGcSuppressionWindow();
            Toast.makeText(this, "Armed 3000ms ART GC Suppression Window", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.lfu_cache_demo).setOnClickListener(v -> {
            LFUCache<String, String> cache = new LFUCache<>(2);
            cache.put("feed_card_1", "Card 1 Data");
            cache.put("feed_card_2", "Card 2 Data");
            cache.get("feed_card_1"); // freq(feed_card_1) = 2, freq(feed_card_2) = 1
            cache.put("feed_card_3", "Card 3 Data"); // Evicts feed_card_2 (minFreq = 1)
            String status = "LFU kept card_1=" + (cache.get("feed_card_1") != null)
                    + ", evicted card_2=" + (cache.get("feed_card_2") == null);
            Log.i(TAG, status);
            Toast.makeText(this, status, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.boost_thread_priority).setOnClickListener(v -> {
            Process.setThreadPriority(Process.myTid(), Process.THREAD_PRIORITY_URGENT_DISPLAY);
            int renderTid = findRenderThreadTid();
            if (renderTid > 0) {
                Process.setThreadPriority(renderTid, -19);
            }
            String msg = "Boosted UI Thread (" + Process.myTid() + ") & RenderThread (" + renderTid + ")";
            Log.i(TAG, msg);
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.thread_pool_demo).setOnClickListener(v -> {
            MonitoredThreadPool.cpuPool().execute(() -> Log.i(TAG, "Executed CPU-bound task"));
            MonitoredThreadPool.ioPool().execute(() -> Log.i(TAG, "Executed I/O-bound task"));
            Toast.makeText(this, "Dispatched tasks to MonitoredThreadPool", Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Chapter 5.7.2: Discovers the RenderThread TID by scanning /proc/self/task/<tid>/comm.
     */
    private int findRenderThreadTid() {
        File taskDir = new File("/proc/self/task");
        File[] tasks = taskDir.listFiles();
        if (tasks == null) return -1;
        for (File task : tasks) {
            File commFile = new File(task, "comm");
            try (BufferedReader reader = new BufferedReader(new FileReader(commFile))) {
                String name = reader.readLine();
                if (name != null && name.trim().equals("RenderThread")) {
                    return Integer.parseInt(task.getName());
                }
            } catch (Exception ignored) {
            }
        }
        return -1;
    }

    private native long[] getProcessCpuTicks();

    private native boolean bindCurrentThreadToBigCores();

    private native void enableGcSuppressionWindow();
}
