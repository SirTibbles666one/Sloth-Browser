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
    private boolean moved;

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
                moved = false;
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
                    moved = true;
                    if (view.getParent() != null) {
                        // the scroller must stop treating this as its own flick
                        view.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (listener != null) {
                        // the strip has no height to keep under the finger, so the travel
                        // before the claim is simply not carried
                        listener.onDragStart(downY, 0f);
                    }
                }
                float step = event.getY() - lastY;
                lastY = event.getY();
                lastT = event.getEventTime();
                if (listener != null) {
                    listener.onDrag(step);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (dragging) {
                    long dt = Math.max(1, event.getEventTime() - lastT);
                    float velocity = (event.getY() - lastY) / dt;
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
