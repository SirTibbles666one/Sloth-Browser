package com.sloth.browser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

/**
 * The seam where the sheet meets the bar.
 *
 * <p>The bar and the drawer are one surface, which is right for the hand and wrong for the
 * eye: a single flat panel with no edge reads as a screenshot of a panel. Browsers solve
 * this with a shadow under the toolbar, but a shadow alone says "something is on top". This
 * adds the other half of the idea - a short accent line lying along the seam whose length
 * grows as the sheet comes up. Closed, there is nothing; half open, a stub; fully open, a
 * line that spans the page. It is the hinge of the sheet, drawn.</p>
 *
 * <p>Everything here is paint: no layout, no children, no touch. It cannot change how the
 * drawer measures, which is the fault that has broken this surface twice before.</p>
 */
final class SlothEdge extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float open;

    SlothEdge(Context context) {
        super(context);
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setClickable(false);
        setFocusable(false);
    }

    /** How far open the sheet is, 0 closed to 1 fully open. */
    void open(float fraction) {
        float next = Math.max(0f, Math.min(1f, fraction));
        if (Math.abs(next - open) > 0.004f) {
            open = next;
            invalidate();
        }
    }

    float open() {
        return open;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;

        // the sheet is above the page: a short fall of shadow just inside its top edge
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0x8C000000, 0x00000000}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        // and the hinge: a line that tells you how far this thing is open
        float stroke = Math.max(1.6f, 2.2f * density);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(stroke);
        paint.setColor(SlothTheme.lineAt(Math.round(0x44 + 0x74 * open)));
        float inset = w * (0.34f - 0.20f * open);
        float y = stroke / 2f + density * 2f;
        canvas.drawLine(inset, y, w - inset, y, paint);
    }
}
