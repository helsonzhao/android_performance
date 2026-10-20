package com.example.performance_optimize.memory;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chapter 3.3: Bitmap Governance — Oversized Bitmap Detection, Two-Pass Downsampling,
 * and ReferenceQueue-Based Bitmap Leak Tracking.
 */
public final class BitmapGovernanceHelper {
    private static final String TAG = "BitmapGovernance";
    private static final float OVERSIZE_WARNING_RATIO = 1.5f;

    private static final ReferenceQueue<Bitmap> BITMAP_REF_QUEUE = new ReferenceQueue<>();
    private static final Map<WeakReference<Bitmap>, String> TRACKED_BITMAPS = new ConcurrentHashMap<>();

    private BitmapGovernanceHelper() {
    }

    /**
     * Chapter 3.3.2: Inspects an ImageView's Drawable after layout and logs a warning
     * if the decoded Bitmap dimensions exceed the ImageView's physical dimensions.
     */
    public static void inspectImageViewBitmap(final ImageView imageView, final String sceneTag) {
        imageView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                imageView.getViewTreeObserver().removeOnPreDrawListener(this);
                Drawable drawable = imageView.getDrawable();
                if (drawable instanceof BitmapDrawable) {
                    Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                    if (bitmap != null) {
                        int viewW = imageView.getWidth();
                        int viewH = imageView.getHeight();
                        int bmpW = bitmap.getWidth();
                        int bmpH = bitmap.getHeight();
                        if (viewW > 0 && viewH > 0 &&
                                (bmpW > viewW * OVERSIZE_WARNING_RATIO || bmpH > viewH * OVERSIZE_WARNING_RATIO)) {
                            Log.w(TAG, String.format(
                                    "[Oversized Bitmap] scene=%s, bitmap=%dx%d (%d bytes), view=%dx%d",
                                    sceneTag, bmpW, bmpH, bitmap.getAllocationByteCount(), viewW, viewH));
                        }
                    }
                }
                return true;
            }
        });
    }

    /**
     * Chapter 3.3.2: Two-pass Bitmap decoding using inJustDecodeBounds and power-of-two inSampleSize.
     */
    public static Bitmap decodeSampledBitmapFromResource(
            Resources res, int resId, int reqWidth, int reqHeight) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bitmap = BitmapFactory.decodeResource(res, resId, options);
        trackBitmapAllocation(bitmap, "Resource#" + resId);
        return bitmap;
    }

    public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    /**
     * Chapter 3.3.3: Tracks Bitmap lifecycle via WeakReference + ReferenceQueue.
     */
    public static void trackBitmapAllocation(Bitmap bitmap, String allocationSite) {
        if (bitmap == null) return;
        pollCollectedBitmaps();
        WeakReference<Bitmap> ref = new WeakReference<>(bitmap, BITMAP_REF_QUEUE);
        TRACKED_BITMAPS.put(ref, allocationSite + " (" + bitmap.getAllocationByteCount() + "B)");
    }

    public static void pollCollectedBitmaps() {
        WeakReference<? extends Bitmap> ref;
        while ((ref = (WeakReference<? extends Bitmap>) BITMAP_REF_QUEUE.poll()) != null) {
            String site = TRACKED_BITMAPS.remove(ref);
            Log.i(TAG, "Bitmap reclaimed by GC: " + site);
        }
    }
}
