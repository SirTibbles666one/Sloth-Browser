package com.sloth.browser;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

/**
 * How the drawer moves: a finger dragging it, a release choosing a seat, and the settle that
 * carries it there.
 *
 * <p>This was twenty statics spread through SlothShell. It holds them now, one instance per
 * window, and asks its host for the things that are the shell's business - the drawer's
 * height and how to set it, the seats, the page that follows the sheet. Every rule here was
 * paid for by a bug, and the tests in SlothShellTest that drive the shell's gesture are what
 * keep them:</p>
 *
 * <ul>
 *   <li>A finger landing on a moving sheet takes it: the settle in flight stops at once.</li>
 *   <li>The sheet picks up the travel spent claiming the gesture, so nothing lurches.</li>
 *   <li>Moves are coalesced: the newest is applied once per turn of the main loop.</li>
 *   <li>Only the drag that started can end: a stray release does nothing.</li>
 *   <li>Every frame of a settle checks its generation; a stopped one leaves nothing
 *       half-transparent behind.</li>
 *   <li>A settle is timed by the clock, not by frames, so a dropped frame skips ahead
 *       instead of stretching it.</li>
 * </ul>
 */
final class SlothSheet {

    /** What the sheet needs from the shell. Called on the main thread. */
    interface Host {
        /** The drawer's height now. */
        int height();

        /** Set the drawer's height: the shell lays everything else out around it. */
        void apply(int height);

        /** The furthest a finger may pull the sheet. */
        int limit();

        /** The resting heights, ascending, starting at 0. */
        int[] seats();

        int peek();

        /** How close counts as "already there", in pixels. */
        int slop();

        int px(int dp);

        boolean reducedMotion();

        /** The finger is up: the page may take its real offset now. */
        void released();

        /** A release chose this seat: the shell settles the sheet there. */
        void landing(int target);

        /** A settle reached its seat. */
        void settled(int target);

        /** A settle was stopped part way: undo anything it had faded. */
        void abandoned();
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Host host;
    private final View view;

    /** Which settle a frame belongs to; a frame from an older one stops. */
    private int gen;
    private boolean animating;
    private boolean dragging;
    private float startH;
    /** The newest move not yet applied, or MIN_VALUE. */
    private int pending = Integer.MIN_VALUE;
    private boolean posted;

    /*
     * The last drag, kept as numbers. "It still feels wrong" is the most expensive report this
     * project gets, because a drag exists only while a finger is on the glass: these describe
     * one - where it started, how many moves were applied, where it was let go, how fast, and
     * which seat it chose.
     */
    private int traceFrom = -1;
    private int traceTo = -1;
    private int traceMoves;
    private int tracePeak;
    private float traceVelocity;
    private boolean traceFlicked;
    private long traceMs;

    /** Settle frames drawn, and those that came more than two frames late. For Health. */
    static int settleFrames;
    static int slowFrames;

    SlothSheet(View view, Host host) {
        this.view = view;
        this.host = host;
    }

    boolean dragging() {
        return dragging;
    }

    boolean animating() {
        return animating;
    }

    /** Stop any settle in flight. Its next frame sees it is stale and tidies up. */
    void stop() {
        gen++;
    }

    /** The window is going: nothing of this sheet may run again. */
    void dispose() {
        gen++;
        animating = false;
        dragging = false;
        pending = Integer.MIN_VALUE;
        posted = false;
    }

    /** A finger claimed the sheet; {@code carry} is the travel spent claiming it. */
    void grab(float carry) {
        // a flick may still be flying when the finger lands. Two writers to the same height
        // is the drawer fighting the hand that grabbed it
        animating = false;
        gen++;
        int h = host.height();
        // the sheet is under the finger: it takes up the travel spent claiming the gesture,
        // so nothing lurches when the drag begins
        startH = Math.max(0, h + Math.round(carry));
        dragging = true;
        traceFrom = h;
        tracePeak = Math.round(startH);
        traceMoves = 0;
        traceVelocity = 0f;
        traceTo = -1;
        traceFlicked = false;
        traceMs = SystemClock.uptimeMillis();
    }

    /** The finger has moved {@code dy} from where it claimed the sheet. */
    void move(float dy) {
        // a digitiser can deliver two moves before a frame is drawn. Keep the newest and
        // apply it once per loop turn: the sheet still sits under the finger, and the tree is
        // measured once instead of twice
        pending = (int) Math.max(0, Math.min(startH + dy, host.limit()));
        if (posted) {
            return;
        }
        posted = true;
        final int mine = gen;
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                posted = false;
                if (mine == gen && dragging && pending != Integer.MIN_VALUE) {
                    int h = pending;
                    pending = Integer.MIN_VALUE;
                    traceMoves++;
                    tracePeak = h;
                    host.apply(h);
                }
            }
        });
    }

    /** The finger is up at {@code velocity} px/ms, positive opening. */
    void release(float velocity) {
        // only the drag that started may end it. A scrollable child reporting its own release
        // used to settle a sheet nobody had touched, which is one way the drawer appeared to
        // shut itself
        if (!dragging) {
            return;
        }
        // whatever the last turn had not applied yet, applied now, so the settle starts from
        // where the finger left the sheet and not one frame behind it
        if (pending != Integer.MIN_VALUE) {
            int h = Math.max(0, Math.min(pending, host.limit()));
            pending = Integer.MIN_VALUE;
            host.apply(h);
        }
        dragging = false;
        traceVelocity = velocity;
        traceFlicked = Math.abs(velocity) > SlothSeats.FLICK;
        traceMs = SystemClock.uptimeMillis() - traceMs;
        // the commit: the page takes its real offset once, now that the finger is up
        host.released();
        land(velocity);
    }

    /** Choose the seat for a sheet let go at {@code velocity}, and hand it to the host. */
    void land(float velocity) {
        int[] seats = host.seats();
        int full = seats[seats.length - 1];
        int target = SlothSeats.choose(seats, host.height(), velocity, host.peek(), host.slop(),
                traceFrom >= full - host.slop(), true);
        traceTo = target;
        host.landing(target);
    }

    /** Carry the sheet to {@code target}, eased, timed by the clock. */
    void settle(final int target) {
        final int from = host.height();
        if (host.reducedMotion() || from == target) {
            view.setAlpha(1f);
            view.setTranslationY(0);
            host.apply(target);
            return;
        }
        view.setVisibility(View.VISIBLE);
        view.setAlpha(1f);
        view.setTranslationY(0);
        // A 500 dp drop and a 40 dp nudge cannot take the same time, and a frame count cannot
        // either: at 16 ms a frame the snap stretched whenever the phone dropped one, which read
        // as the settle stuttering. The duration is in milliseconds and the progress is read
        // from the clock, so a slow frame skips ahead instead of stretching the animation.
        int distance = Math.abs(target - from);
        final int duration = Math.max(120, Math.min(260, Math.round(120 + distance * 0.35f)));
        final int delta = target - from;
        final boolean opening = from < host.px(8);
        if (opening) {
            // content settles in behind the sheet rather than snapping into place
            view.setAlpha(0.85f);
            view.setTranslationY(host.px(10));
        }
        final int mine = gen;
        animating = true;
        final long started = SystemClock.uptimeMillis();
        MAIN.post(new Runnable() {
            private long last = started;

            @Override
            public void run() {
                long at = SystemClock.uptimeMillis();
                settleFrames++;
                if (at - last > 33) {
                    // a frame posted 16 ms after the last arrived two frames late or worse
                    slowFrames++;
                }
                last = at;
                if (mine != gen) {
                    // the sheet moved on mid-flight: leave nothing half-transparent behind
                    view.setAlpha(1f);
                    view.setTranslationY(0);
                    host.abandoned();
                    animating = false;
                    return;
                }
                long now = SystemClock.uptimeMillis();
                float t = Math.min(1f, (now - started) / (float) duration);
                float eased = 1f - (1f - t) * (1f - t);
                host.apply(from + Math.round(delta * eased));
                if (opening) {
                    view.setAlpha(0.85f + 0.15f * eased);
                    view.setTranslationY(host.px(Math.round(10 * (1f - eased))));
                }
                if (t < 1f) {
                    MAIN.postDelayed(this, 16);
                } else {
                    animating = false;
                    // the settle's last word: the page takes its exact offset and the
                    // translation that followed the sheet is cleared in one layout
                    host.settled(target);
                }
            }
        });
    }

    /** The last drag as a line of numbers, for Diagnostics and the harness. */
    String trace(String source) {
        return traceFrom + "->" + tracePeak + " moves=" + traceMoves + " v=" + traceVelocity
                + " seat=" + traceTo + " ms=" + traceMs + " by=" + source;
    }

    /** The last drag in words, for the Diagnostics page. */
    String describe() {
        if (traceFrom < 0) {
            return "none yet";
        }
        return traceFrom + " -> " + tracePeak + "px in " + traceMoves + " moves, " + traceMs
                + "ms, released at " + Math.round(traceVelocity * 1000) + "px/s"
                + (traceFlicked ? " (flick)" : " (slow)") + ", settled at " + traceTo + "px";
    }
}
