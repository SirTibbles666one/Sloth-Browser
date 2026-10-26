package com.sloth.browser;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ScrollView;

/**
 * The drawer's content scrolls - but at the top of the list an upward drag belongs to
 * the drawer itself, not to the list. Without this the only way to close the drawer was
 * to find the bar, which is why it felt finicky: the finger was on the content, and the
 * content just scrolled.
 *
 * The rule: while the sheet is not fully open, a vertical drag anywhere on the content
 * moves the sheet - both directions, because opening it further is the same gesture as
 * closing it and needing the bar to go down was the other half of "finicky". Once the
 * sheet is at its full seat, the list scrolls, and only at a list edge does the drag
 * return to the sheet.
 */
public class SlothSheetScroll extends ScrollView {

    /** Asked whether the sheet has reached its full seat yet. */
    public interface AtFull {
        boolean atFull();
    }

    private SlothBar.Listener listener;
    private AtFull atFullForTheSheet;
    private final int slop;
    private float downX, downY, lastY;
    private long lastT;
    private boolean dragging;

    public SlothSheetScroll(Context context) {
        super(context);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        setOverScrollMode(OVER_SCROLL_NEVER);
        setVerticalScrollBarEnabled(true);
    }

    public void setListener(SlothBar.Listener listener) {
        this.listener = listener;
    }

    /** Where the sheet answers whether it is open all the way. */
    public void setAtFull(AtFull atFull) {
        this.atFullForTheSheet = atFull;
    }

    /**
     * Who owns a vertical swipe on the content: the panel or the list?
     *
     * <p>Below the full seat, the panel - in either direction, because opening it further is the
     * same gesture as closing it. At the full seat the list scrolls, and only when it has run out
     * in that direction does the drag come back to the panel.</p>
     *
     * @param canScrollUp   the list can still scroll towards its top
     * @param canScrollDown the list can still scroll towards its end
     */
    static boolean ownsTheSwipe(float dy, float dx, int slop, boolean atFull,
            boolean canScrollUp, boolean canScrollDown) {
        float travel = Math.abs(dy);
        if (travel <= slop || travel <= Math.abs(dx) * 1.05f) {
            return false;
        }
        if (!atFull) {
            return true;
        }
        return dy > 0 ? !canScrollUp : !canScrollDown;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (listener == null) {
            return super.onInterceptTouchEvent(e);
        }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                lastY = downY;
                lastT = e.getEventTime();
                dragging = false;
                return super.onInterceptTouchEvent(e);
            case MotionEvent.ACTION_MOVE: {
                if (dragging) {
                    return true;
                }
                float dy = e.getY() - downY;
                float dx = Math.abs(e.getX() - downX);
                boolean wholeSheet = ownsTheSwipe(dy, dx, slop,
                        atFullForTheSheet == null || atFullForTheSheet.atFull(),
                        canScrollVertically(-1), canScrollVertically(1));
                if (wholeSheet) {
                    dragging = true;
                    holdTheGesture(true);
                    // the scroller knows where the finger landed and where it is now
                    listener.onDragStart(e.getY(), dy);
                    downY = e.getY();
                    // the claim point is also where the speed is measured from, or the
                    // first move after it reads as a jump from wherever the finger started
                    lastY = e.getY();
                    lastT = e.getEventTime();
                    return true;
                }
                return super.onInterceptTouchEvent(e);
            }
            default:
                return super.onInterceptTouchEvent(e);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        // a child may have asked the ancestors to keep out of its gesture; this view is
        // the one that closes the drawer, so that request is cleared and interception is
        // decided here again on every move
        if (!dragging && e.getActionMasked() == MotionEvent.ACTION_MOVE) {
            requestDisallowInterceptTouchEvent(false);
        }
        return super.dispatchTouchEvent(e);
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
                holdTheGesture(true);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dy = e.getY() - downY;
                float dx = Math.abs(e.getX() - downX);
                if (!dragging && getScrollY() <= 0 && dy < -slop && Math.abs(dy) > dx * 1.05f) {
                    dragging = true;
                    listener.onDragStart(e.getY(), dy);
                    downY = e.getY();
                    dy = 0;
                }
                if (dragging) {
                    listener.onDrag(dy);
                    lastY = e.getY();
                    lastT = e.getEventTime();
                    return true;
                }
                return super.onTouchEvent(e);
            }
            case MotionEvent.ACTION_UP:
                // only a drag this view owned ends a drag. A scroll is not a flick of the
                // sheet: reporting one closed the drawer whenever the list was scrolled
                // upwards, which read as the drawer shutting itself for no reason
                if (dragging) {
                    listener.onRelease(velocity(e.getY(), e.getEventTime()));
                }
                dragging = false;
                holdTheGesture(false);
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    listener.onRelease(0f);
                }
                dragging = false;
                holdTheGesture(false);
                return true;
            default:
                return true;
        }
    }

    /** Requests start here; a parent must not take the gesture back mid-drag. */
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
