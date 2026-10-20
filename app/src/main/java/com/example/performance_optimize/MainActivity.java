package com.example.performance_optimize;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;

import com.example.performance_optimize.apksize.ApkSizeOptimizeActivity;
import com.example.performance_optimize.memory.MemoryExampleActivity;
import com.example.performance_optimize.other.OtherOptimizeActivity;
import com.example.performance_optimize.speed.SpeedExampleActivity;
import com.example.performance_optimize.stability.StabilityExampleActivity;

/**
 * Companion Application Entry Activity for "The Art of Android Performance Optimization"
 * by Zijian Zhao.
 */
public class MainActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.memory).setOnClickListener(
                v -> startActivity(new Intent(MainActivity.this, MemoryExampleActivity.class)));

        findViewById(R.id.speed).setOnClickListener(
                v -> startActivity(new Intent(MainActivity.this, SpeedExampleActivity.class)));

        findViewById(R.id.stability).setOnClickListener(
                v -> startActivity(new Intent(MainActivity.this, StabilityExampleActivity.class)));

        findViewById(R.id.package_size).setOnClickListener(
                v -> startActivity(new Intent(MainActivity.this, ApkSizeOptimizeActivity.class)));

        findViewById(R.id.other_optimize).setOnClickListener(
                v -> startActivity(new Intent(MainActivity.this, OtherOptimizeActivity.class)));
    }
}