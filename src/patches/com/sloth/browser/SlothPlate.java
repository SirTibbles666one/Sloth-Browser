package com.sloth.browser;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.DisplayMetrics;

/**
 * The new-tab "Sloth" plate, redrawn correctly.
 *
 * Three defects in TabSquares.homePlate():
 *  - the bitmap is cached in a static with no density key, so after a display
 *    size change (or on a foldable) the old pixel-sized plate is stretched;
 *  - drawText() y is the baseline, not the centre, so the word sat about a
 *    fifth of its height high - the plate never looked centred;
 *  - the cache is never rebuilt when the bitmap is recycled elsewhere.
 *
 * Same size (280x150dp), same colours as the shipped plate.
 */
public final class SlothPlate {

    private static final int BACK = -15392742;
    private static final int INK = -1579804;

    private static Bitmap plate;
    private static int plateWidth;
    private static int plateHeight;

    private SlothPlate() {
    }

    public static Bitmap plate(Activity activity) {
        if (activity == null) {
            return null;
        }
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        float density = metrics.density > 0 ? metrics.density : 1f;
        int width = Math.max(1, Math.round(280 * density));
        int height = Math.max(1, Math.round(150 * density));

        Bitmap cached = plate;
        if (cached != null && !cached.isRecycled()
                && cached.getWidth() == width && cached.getHeight() == height) {
            return cached;
        }

        Bitmap made;
        try {
            made = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        } catch (Throwable oom) {
            return cached;
        }
        Canvas canvas = new Canvas(made);
        canvas.drawColor(BACK);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(INK);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(22 * density);

        // centre on the cap height, not on the baseline
        Paint.FontMetrics metricsOf = paint.getFontMetrics();
        float middle = height / 2f;
        float baseline = middle - ((metricsOf.ascent + metricsOf.descent) / 2f);
        canvas.drawText("Sloth", width / 2f, baseline, paint);

        plate = made;
        plateWidth = width;
        plateHeight = height;
        return made;
    }

    /** Called from the bar when the app is recreated; keeps the cache honest. */
    public static void forget() {
        plate = null;
        plateWidth = 0;
        plateHeight = 0;
    }
}
