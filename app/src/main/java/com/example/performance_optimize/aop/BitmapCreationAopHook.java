package com.example.performance_optimize.aop;

import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;

import com.flyjingfish.android_aop_annotation.anno.AndroidAopReplaceClass;
import com.flyjingfish.android_aop_annotation.anno.AndroidAopReplaceMethod;

/**
 * Chapter 3.3.2: Intercepts Bitmap.createBitmap(int, int, Bitmap.Config) at compile time
 * using FlyJingFish/AndroidAOP to monitor Bitmap memory footprint and apply fallback downsampling.
 */
@AndroidAopReplaceClass("android.graphics.Bitmap")
public final class BitmapCreationAopHook {
    private static final String TAG = "BitmapCreationAopHook";

    private BitmapCreationAopHook() {
    }

    @AndroidAopReplaceMethod(
            "android.graphics.Bitmap createBitmap(int, int, android.graphics.Bitmap$Config)"
    )
    public static Bitmap createBitmap(int width, int height, Bitmap.Config config) {
        monitorAndOptimizeBitmap(width, height, config);
        return Bitmap.createBitmap(width, height, config);
    }

    private static void monitorAndOptimizeBitmap(int width, int height, Bitmap.Config config) {
        float factor = 1f;
        if (config == Bitmap.Config.ARGB_8888) {
            factor = 4f;
        } else if (config == Bitmap.Config.ARGB_4444 || config == Bitmap.Config.RGB_565) {
            factor = 2f;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && config == Bitmap.Config.RGBA_F16) {
            factor = 8f;
        }
        float sizeMb = width * height * factor / (1024f * 1024f);
        Log.i(TAG, "Create Bitmap Size:" + sizeMb + "M Width:" + width + " Height:" + height);
    }
}
