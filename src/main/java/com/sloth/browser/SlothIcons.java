package com.sloth.browser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * The bar's controls, drawn rather than typed.
 *
 * <p>They were glyphs from a text font: a chevron, a circular arrow, a plus, three dots. Text
 * glyphs sit on a baseline and carry their own bearings, so no two of them line up - the
 * chevron sits low, the dots sit high, and the stroke weight is whatever that font decided.
 * Drawn, they share one stroke width, one cap, and one optical size, which is the difference
 * between a row of icons and a row of characters.</p>
 */
final class SlothIcons extends View {

    /** The controls the bar carries. */
    enum Kind { BACK, FORWARD, RELOAD, PLUS, MENU, CLOSE, LOCK, TABS, SEARCH }

    private Kind kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private SlothIcons(Context context, Kind kind) {
        super(context);
        this.kind = kind;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setClickable(true);
        setFocusable(true);
    }

    /** A control of the given size, in the palette's ink unless told otherwise. */
    static SlothIcons of(Context context, Kind kind, int sizeDp, int colour) {
        SlothIcons icon = new SlothIcons(context, kind);
        icon.paint.setColor(colour);
        int px = Math.round(sizeDp * context.getResources().getDisplayMetrics().density);
        icon.setLayoutParams(new android.widget.LinearLayout.LayoutParams(px, px));
        return icon;
    }

    /** Change which control this is, for a slot whose meaning depends on the page. */
    void kind(Kind next) {
        if (next != kind) {
            kind = next;
            invalidate();
        }
    }

    /** The ink this control is drawn in; the bar's lock changes colour with the address. */
    int colour() {
        return paint.getColor();
    }

    /** Recolour in place: the bar's lock turns amber when the address is not secure. */
    void tint(int colour) {
        paint.setColor(colour);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        float cx = w / 2f;
        float cy = h / 2f;
        float r = Math.min(w, h) * 0.5f;
        paint.setStrokeWidth(Math.max(2.0f, r * 0.17f));
        path.reset();

        switch (kind) {
            case BACK: {
                // chevrons read heavier than a cross at the same stroke, so they are drawn a
                // little smaller to sit at the same optical weight as the rest of the row
                path.moveTo(cx + r * 0.26f, cy - r * 0.44f);
                path.lineTo(cx - r * 0.30f, cy);
                path.lineTo(cx + r * 0.26f, cy + r * 0.44f);
                break;
            }
            case FORWARD: {
                path.moveTo(cx - r * 0.26f, cy - r * 0.44f);
                path.lineTo(cx + r * 0.30f, cy);
                path.lineTo(cx - r * 0.26f, cy + r * 0.44f);
                break;
            }
            case RELOAD: {
                // an arc with a gap, and an arrowhead on the leading end
                path.addArc(cx - r * 0.62f, cy - r * 0.62f, cx + r * 0.62f, cy + r * 0.62f,
                        40f, 280f);
                path.moveTo(cx + r * 0.10f, cy - r * 0.70f);
                path.lineTo(cx + r * 0.52f, cy - r * 0.44f);
                path.lineTo(cx + r * 0.16f, cy - r * 0.20f);
                break;
            }
            case PLUS: {
                // a cross has less ink than a chevron for the same box, so it is drawn wider
                path.moveTo(cx - r * 0.66f, cy);
                path.lineTo(cx + r * 0.66f, cy);
                path.moveTo(cx, cy - r * 0.66f);
                path.lineTo(cx, cy + r * 0.66f);
                break;
            }
            case MENU: {
                for (int i = -1; i <= 1; i++) {
                    float y = cy + i * r * 0.40f;
                    path.moveTo(cx - r * 0.58f, y);
                    path.lineTo(cx + r * 0.58f, y);
                }
                break;
            }
            case LOCK: {
                // a padlock, drawn: as a text glyph it arrived as a colour emoji on most
                // phones - the loudest thing in the bar, and the only piece not ours
                path.addRoundRect(new android.graphics.RectF(cx - r * 0.42f, cy - r * 0.02f,
                        cx + r * 0.42f, cy + r * 0.54f), r * 0.12f, r * 0.12f,
                        Path.Direction.CW);
                path.addArc(new android.graphics.RectF(cx - r * 0.28f, cy - r * 0.52f,
                        cx + r * 0.28f, cy + r * 0.06f), 180f, 180f);
                break;
            }
            case SEARCH: {
                // the field's leading glyph when there is no address to be secure about
                path.addCircle(cx - r * 0.10f, cy - r * 0.10f, r * 0.42f, Path.Direction.CW);
                path.moveTo(cx + r * 0.20f, cy + r * 0.20f);
                path.lineTo(cx + r * 0.52f, cy + r * 0.52f);
                break;
            }
            case TABS: {
                // a stack of two, so a count beside it is a count of tabs
                path.addRoundRect(new android.graphics.RectF(cx - r * 0.52f, cy - r * 0.30f,
                        cx + r * 0.26f, cy + r * 0.42f), r * 0.14f, r * 0.14f, Path.Direction.CW);
                path.addRoundRect(new android.graphics.RectF(cx - r * 0.26f, cy - r * 0.44f,
                        cx + r * 0.52f, cy + r * 0.28f), r * 0.14f, r * 0.14f, Path.Direction.CW);
                break;
            }
            case CLOSE: {
                path.moveTo(cx - r * 0.42f, cy - r * 0.42f);
                path.lineTo(cx + r * 0.42f, cy + r * 0.42f);
                path.moveTo(cx + r * 0.42f, cy - r * 0.42f);
                path.lineTo(cx - r * 0.42f, cy + r * 0.42f);
                break;
            }
            default:
                break;
        }
        canvas.drawPath(path, paint);
    }
}
