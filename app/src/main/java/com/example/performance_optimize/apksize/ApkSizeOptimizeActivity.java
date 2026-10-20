package com.example.performance_optimize.apksize;

import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.performance_optimize.R;

import java.io.File;
import java.util.List;

/**
 * Chapter 9: Practical Optimization of Package Size Demo Activity.
 * Covers:
 * - 9.1.2 Duplicate APK Entry Scanner (CRC-32 checksum comparison)
 * - 9.6.2 Dynamic Resource Pack Loading (AssetManager.addAssetPath)
 * - 9.7.2 Dynamic Class Loading (DexClassLoader)
 * - 9.8.2 Dynamic Native .so Directory Injection (DexPathList.nativeLibraryDirectories)
 */
public class ApkSizeOptimizeActivity extends AppCompatActivity {
    private static final String TAG = "ApkSizeOptimize";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apk_size_optimize);

        findViewById(R.id.scan_duplicate_res).setOnClickListener(v -> {
            String apkPath = getApplicationInfo().sourceDir;
            List<String> duplicates = DynamicPluginManager.scanDuplicateApkEntries(apkPath);
            String msg = "Scanned APK: found " + duplicates.size() + " duplicate entries";
            Log.i(TAG, msg);
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.dynamic_res_load).setOnClickListener(v -> {
            Resources extRes = DynamicPluginManager.loadExternalResources(this, getApplicationInfo().sourceDir);
            boolean ok = extRes != null;
            Toast.makeText(this, "Dynamic AssetManager.addAssetPath loaded: " + ok, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.dynamic_dex_load).setOnClickListener(v -> {
            Class<?> loaded = DynamicPluginManager.loadExternalClass(
                    this,
                    getApplicationInfo().sourceDir,
                    "com.example.performance_optimize.apksize.DynamicPluginManager");
            Toast.makeText(this, "DexClassLoader loaded: " + (loaded != null ? loaded.getSimpleName() : "null"), Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.dynamic_so_inject).setOnClickListener(v -> {
            File customSoDir = new File(getFilesDir(), "dynamic_libs");
            if (!customSoDir.exists()) {
                customSoDir.mkdirs();
            }
            boolean injected = DynamicPluginManager.injectNativeLibraryDir(getClassLoader(), customSoDir);
            Toast.makeText(this, "Injected dynamic .so dir into DexPathList: " + injected, Toast.LENGTH_SHORT).show();
        });
    }
}
