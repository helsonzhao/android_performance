package com.example.performance_optimize.aop;

import android.graphics.Bitmap;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import com.flyjingfish.android_aop_annotation.anno.AndroidAopReplaceClass;
import com.flyjingfish.android_aop_annotation.anno.AndroidAopReplaceMethod;

/**
 * Chapter 3.3.2: Intercepts ImageView.setImageBitmap(Bitmap) at compile time using
 * FlyJingFish/AndroidAOP to detect oversized Bitmaps whose dimensions exceed the
 * target ImageView's physical view dimensions.
 */
@AndroidAopReplaceClass("android.widget.ImageView")
public final class ImageViewBitmapHook {
    private static final String TAG = "ImageViewBitmapHook";
    private static final int MAX_REASONABLE_RATIO = 2;

    private ImageViewBitmapHook() {
    }

    @AndroidAopReplaceMethod("void setImageBitmap(android.graphics.Bitmap)")
    public static void setImageBitmap(final ImageView imageView, final Bitmap bitmap) {
        if (bitmap != null) {
            int viewWidth = imageView.getWidth();
            int viewHeight = imageView.getHeight();
            if (viewWidth > 0 && viewHeight > 0) {
                checkBitmapSize(bitmap, viewWidth, viewHeight);
            } else {
                imageView.getViewTreeObserver().addOnPreDrawListener(
                        new ViewTreeObserver.OnPreDrawListener() {
                            @Override
                            public boolean onPreDraw() {
                                imageView.getViewTreeObserver().removeOnPreDrawListener(this);
                                checkBitmapSize(bitmap, imageView.getWidth(), imageView.getHeight());
                                return true;
                            }
                        });
            }
        }
        imageView.setImageBitmap(bitmap);
    }

    private static void checkBitmapSize(Bitmap bitmap, int viewWidth, int viewHeight) {
        if (viewWidth <= 0 || viewHeight <= 0) return;
        if (bitmap.getWidth() > viewWidth * MAX_REASONABLE_RATIO
                && bitmap.getHeight() > viewHeight * MAX_REASONABLE_RATIO) {
            Log.w(TAG, "Oversized Bitmap detected! Bitmap="
                    + bitmap.getWidth() + "x" + bitmap.getHeight()
                    + ", ImageView=" + viewWidth + "x" + viewHeight,
                    new Throwable("Oversized Bitmap StackTrace"));
        }
    }
}
