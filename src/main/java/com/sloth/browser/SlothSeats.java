package com.sloth.browser;

/**
 * Where a released drawer lands.
 *
 * <p>This is the arithmetic that kept being wrong: a flick that must always move somewhere, an
 * upward flick that moves one seat down and never lands on the peek (the peek is what a downward
 * pull finds first, not a stop on the way out), a release near the shut end that shuts, and a
 * slow release that settles on the nearest seat after 140 ms of projection. It lived inside a 500-line method, so the only way to test it was to build a whole
 * window and drive touches through it.</p>
 *
 * <p>Pure on purpose: no views, no density, no clock. The caller passes the seats in pixels and
 * the slop it wants. Anything that needs a screen is the caller's business.</p>
 */
final class SlothSeats {

    private SlothSeats() {
    }

    /** A release faster than this (px/ms) counts as a throw. */
    static final float FLICK = 0.55f;

    /** How far a slow release is projected, in milliseconds. */
    static final float PROJECTION_MS = 140f;

    /**
     * The seat a drawer of {@code height} should animate to.
     *
     * @param seats    every resting place, ascending, starting at 0
     * @param height   where the drawer is now
     * @param velocity px/ms, positive is downward (opening)
     * @param peek     the peek seat, which an upward flick passes through
     * @param slop     how close counts as "already there", in pixels
     */
    static int choose(int[] seats, int height, float velocity, int peek, int slop) {
        if (seats.length == 0) {
            return height;
        }
        int target = -1;
        if (Math.abs(velocity) > FLICK) {
            if (velocity > 0) {
                for (int seat : seats) {
                    if (seat > height + slop) {
                        target = seat;
                        break;
                    }
                }
                if (target < 0) {
                    target = seats[seats.length - 1];
                }
            } else {
                for (int i = seats.length - 1; i >= 0; i--) {
                    if (seats[i] < height - slop) {
                        if (seats[i] == peek) {
                            continue;
                        }
                        target = seats[i];
                        break;
                    }
                }
                if (target < 0) {
                    target = 0;
                }
            }
        } else if (velocity <= 0.05f && height <= peek + slop * 2 + 4) {
            target = 0;
        } else {
            float projected = height + velocity * PROJECTION_MS;
            float best = Float.MAX_VALUE;
            for (int seat : seats) {
                float distance = Math.abs(seat - projected);
                if (distance < best) {
                    best = distance;
                    target = seat;
                }
            }
        }
        return target;
    }
}
