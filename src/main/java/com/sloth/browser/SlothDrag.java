package com.sloth.browser;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * A vertical drag, on a view that wants sideways gestures for itself.
 *
 * <p>The peek's strip is a horizontal scroller: flicking it walks the tabs. But it is also
 * part of the sheet, and a sheet you can only close by reaching for the bar or the small
 * cross is a sheet that feels stuck. So the strip keeps its sideways gesture and loses the
 * vertical one - claimed only once the finger is clearly going up or down, with the same
 * ratio the bar uses, so a diagonal flick still scrolls rather than closing.</p>
 */
final class SlothDrag implements View.OnTouchListener {

    private final SlothBar.Listener listener;
    private int slop;
    private float downX, downY, lastY;
    private long lastT;
    private boolean dragging;
    private float total;

    SlothDrag(SlothBar.Listener listener) {
        this.listener = listener;
    }

    private int slop(View view) {
        if (slop <= 0) {
            slop = android.view.ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        }
        return slop;
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                downX = event.getX();
                downY = event.getY();
                lastY = downY;
                lastT = event.getEventTime();
                dragging = false;
                return false;
            }
            case MotionEvent.ACTION_MOVE: {
                float dy = event.getY() - downY;
                float dx = event.getX() - downX;
                if (!dragging) {
                    int threshold = slop(view);
                    if (Math.abs(dy) <= threshold || Math.abs(dy) < Math.abs(dx) * 1.08f) {
                        return false;
                    }
                    dragging = true;
                    total = 0f;
                    if (view.getParent() != null) {
                        // the scroller must stop treating this as its own flick
                        view.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (listener != null) {
                        // the strip has no height to keep under the finger, so the travel
                        // before the claim is simply not carried
                        listener.onDragStart(downY, 0f);
                    }
                    // measure speed from the claim, not from where the finger landed
                    lastY = event.getY();
                    lastT = event.getEventTime();
                    return true;
                }
                float step = event.getY() - lastY;
                lastY = event.getY();
                lastT = event.getEventTime();
                // the shell positions the sheet from the travel since the claim, not from the
                // last sample: handing it a per-move delta reset the height every time, which
                // is why a drag on the strip moved the sheet by one step and then stopped
                total += step;
                if (listener != null) {
                    listener.onDrag(total);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (dragging) {
                    // a cancelled gesture has no speed: the system took the finger away, so
                    // the sheet settles rather than flying wherever the last move pointed
                    boolean cancelled = event.getActionMasked() == MotionEvent.ACTION_CANCEL;
                    long dt = Math.max(1, event.getEventTime() - lastT);
                    float velocity = cancelled ? 0f : (event.getY() - lastY) / dt;
                    dragging = false;
                    if (listener != null) {
                        listener.onRelease(velocity);
                    }
                    return true;
                }
                // a tap on the strip is a tap on a tile, not a request to open the sheet
                return false;
            }
            default:
                return false;
        }
    }
}
