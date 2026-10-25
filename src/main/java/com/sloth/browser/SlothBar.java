package com.sloth.browser;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

/**
 * The bar is the handle now: a vertical drag anywhere on it grows it into the drawer.
 *
 * This is the part that has to be careful. The bar is full of things that want the
 * gesture - an address field that takes taps and horizontal swipes, buttons that take
 * clicks - so the surface only INTERCEPTS once the gesture is unmistakably vertical.
 * Until then every event goes to the children untouched, which is why a tap on the
 * field still focuses it and a sideways swipe still changes tab.
 */
public class SlothBar extends LinearLayout {

    public interface Listener {
        /**
         * The gesture was claimed.
         *
         * @param y      where the finger is now, in this view's coordinates
         * @param carry  how far it had already travelled before the claim: the sheet starts
         *               at its old height plus this, so the panel is under the finger
         *               instead of lagging behind it by the touch slop
         */
        void onDragStart(float y, float carry);

        void onDrag(float dy);

        void onRelease(float velocity);

        void onTap();

        /**
         * A sideways flick across the bar.
         *
         * @param dx how far the finger travelled, positive to the right
         * @return true when the shell used it, false when the bar should ignore it
         */
        boolean onSwipe(float dx);
    }

    private final int slop;
    private Listener listener;
    private float downX, downY, lastY;
    private long lastT;
    private boolean dragging;
    /** The finger travelled: a swipe is not a tap, however it ends. */
    private boolean moved;

    public SlothBar(Context context) {
        this(context, null);
    }

    public SlothBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        setOrientation(VERTICAL);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * An EditText asks its parents not to intercept while it handles a gesture. That
     * would make the address field a dead zone for opening the drawer, so the request
     * is declined: intercepting is still conditional on a vertical drag, so nothing
     * that belongs to the field is taken away from it.
     */
    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // deliberately not calling super
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (listener == null) {
            return false;
        }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                lastY = downY;
                lastT = e.getEventTime();
                dragging = false;
                moved = false;
                holdTheGesture(true);
                return false;
            case MotionEvent.ACTION_MOVE: {
                if (dragging) {
                    return true;
                }
                float dy = e.getY() - downY;
                float dx = Math.abs(e.getX() - downX);
                if (Math.abs(dy) > slop || dx > slop) {
                    moved = true;
                }
                // vertical, deliberate, and not a sideways swipe across the address
                if (Math.abs(dy) > slop && Math.abs(dy) > dx * 1.08f) {
                    dragging = true;
                    // the sheet takes up the travel that happened before the claim, then
                    // measures from here: it sits under the finger, and it does not leap
                    float carry = dy;
                    downY = e.getY();
                    lastY = e.getY();
                    listener.onDragStart(downY, carry);
                    return true;
                }
                return false;
            }
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (listener == null) {
            return super.onTouchEvent(e);
        }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                lastY = downY;
                lastT = e.getEventTime();
                dragging = false;
                moved = false;
                holdTheGesture(true);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dy = e.getY() - downY;
                float dx = Math.abs(e.getX() - downX);
                if (Math.abs(dy) > slop || dx > slop) {
                    moved = true;
                }
                if (!dragging && Math.abs(dy) > slop && Math.abs(dy) > dx * 1.08f) {
                    dragging = true;
                    listener.onDragStart(e.getY(), dy);
                    downY = e.getY();
                    dy = 0;
                }
                if (dragging) {
                    listener.onDrag(dy);
                }
                lastY = e.getY();
                lastT = e.getEventTime();
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    listener.onRelease(velocity(e.getY(), e.getEventTime()));
                } else if (!moved) {
                    // a tap on bare bar: the same thing the tab count does
                    listener.onTap();
                }
                boolean sideways = !dragging
                        && Math.abs(e.getX() - downX) > slop * 5f
                        && Math.abs(e.getX() - downX) > Math.abs(e.getY() - downY) * 1.08f;
                dragging = false;
                holdTheGesture(false);
                if (sideways) {
                    listener.onSwipe(e.getX() - downX);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    listener.onRelease(0f);
                }
                dragging = false;
                holdTheGesture(false);
                return true;
        }
        return super.onTouchEvent(e);
    }

    /** Tells the ancestors - a scrolling drawer, most of all - to leave this gesture alone. */
    private void holdTheGesture(boolean hold) {
        android.view.ViewParent parent = getParent();
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(hold);
            parent = parent.getParent();
        }
    }

    private float velocity(float y, long time) {
        long elapsed = Math.max(1, time - lastT);
        return lastT > 0 ? (y - lastY) / (float) elapsed : 0f;
    }
}
