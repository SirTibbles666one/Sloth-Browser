package com.sloth.browser;

import android.app.Activity;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

/**
 * Works out what, if anything, is drawn over the page.
 *
 * <p>Every report of "a window covers the page" so far had to be guessed at from a
 * screenshot, because the shell cannot see the phone. It can see its own window, though.
 * The page is the app's GeckoView; if another visible view covers most of the same
 * rectangle, that view is the answer - by class name and by tag - and naming it turns the
 * next report into a fact instead of a theory.</p>
 */
final class SlothCover {

    private SlothCover() {
    }

    /** The last page view found, so a light timer need not walk the activity's fields. */
    private static View cached;

    /**
     * The app's page view, found by its type: GeckoView is the one that draws the web.
     *
     * <p>Cached per window: the shell asks on every tick now, and walking the activity's
     * fields by reflection ten times a second to find a view that has not moved is work
     * for nothing. A cached view from a different window is discarded, not reused.</p>
     */
    static View page(Activity activity) {
        if (cached != null && cached.getContext() == activity) {
            return cached;
        }
        cached = null;
        View found = findPage(activity);
        cached = found;
        return found;
    }

    private static View findPage(Activity activity) {
        for (Class<?> type = activity.getClass(); type != null; type = type.getSuperclass()) {
            for (java.lang.reflect.Field f : type.getDeclaredFields()) {
                if (f.getType().getName().endsWith("GeckoView")) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(activity);
                        if (v instanceof View) {
                            return (View) v;
                        }
                    } catch (Throwable ignored) {
                        // not reachable in this build; nothing to report from it
                    }
                }
            }
        }
        return null;
    }

    /**
     * Note the page's own state and anything drawn over most of it.
     *
     * <p>Silent when everything is as it should be: a log that fills with "fine" buries
     * the line that matters.</p>
     */
    /**
     * The same diagnosis, as text, for the diagnostics page.
     *
     * <p>probe() writes to the log when something is wrong; this always answers, because a
     * report has to say "page 0x0" or "nothing over it" rather than stay silent.</p>
     */
    /** Forget the cached page view; called when a shell lets go of its window. */
    static void forget() {
        cached = null;
    }

    static String describe(Activity activity) {
        StringBuilder out = new StringBuilder();
        try {
            View page = page(activity);
            if (page == null) {
                return "page: no page view found\npage session: none (no page view)\n";
            }
            Rect pageRect = rectOf(page);
            out.append("page: ").append(simpleName(page))
                    .append(page.getTag() == null ? "" : "[" + page.getTag() + "]")
                    .append(" visible=").append(page.getVisibility() == View.VISIBLE)
                    .append(" shown=").append(page.isShown())
                    .append(" alpha=").append(page.getAlpha())
                    .append(" size=").append(page.getWidth()).append("x").append(page.getHeight());
            if (pageRect != null) {
                out.append(" rect=").append(pageRect.left).append(",").append(pageRect.top)
                        .append(" ").append(pageRect.width()).append("x").append(pageRect.height());
            }
            out.append("\n");
            out.append("page session: ").append(sessionState(page)).append('\n');
            if (pageRect == null) {
                return out.toString();
            }
            Window window = activity.getWindow();
            if (window == null || window.getDecorView() == null) {
                return out.append("window: none").toString();
            }
            java.util.HashSet<View> above = new java.util.HashSet<>();
            for (View v = page; v != null; ) {
                above.add(v);
                android.view.ViewParent parent = v.getParent();
                v = parent instanceof View ? (View) parent : null;
            }
            StringBuilder covers = new StringBuilder();
            int found = findCovers(window.getDecorView(), page, pageRect, above, covers, 0);
            out.append("over the page: ").append(found == 0 ? "nothing" : covers.toString());
        } catch (Throwable t) {
            out.append("page probe failed: ").append(t.getClass().getSimpleName());
        }
        return out.toString();
    }

    /**
     * Whether the page view is still attached to a browsing session.
     *
     * <p>A GeckoView with a page on screen and no session is a black rectangle: the engine
     * has nothing to draw into it. That is a different fault from a page being covered, and
     * the two look identical in a photograph.</p>
     */
    private static String sessionState(View page) {
        try {
            for (Class<?> type = page.getClass(); type != null; type = type.getSuperclass()) {
                for (java.lang.reflect.Field f : type.getDeclaredFields()) {
                    String name = f.getType().getName();
                    if (!name.endsWith("GeckoSession") && !name.equals("session")) {
                        continue;
                    }
                    f.setAccessible(true);
                    Object value = f.get(page);
                    return value == null ? "none (the engine has nothing to draw)"
                            : "attached";
                }
            }
        } catch (Throwable ignored) {
        }
        return "not readable";
    }

    static void probe(Activity activity, String when) {
        try {
            View page = page(activity);
            if (page == null) {
                SlothLog.note("no page view found (" + when + ")");
                return;
            }
            if (page.getVisibility() != View.VISIBLE || page.getWidth() == 0 || page.getHeight() == 0) {
                SlothLog.note("page is " + (page.getVisibility() != View.VISIBLE ? "hidden" : "empty")
                        + " " + page.getWidth() + "x" + page.getHeight() + " (" + when + ")");
            }
            Rect pageRect = rectOf(page);
            if (pageRect == null) {
                return;
            }
            Window window = activity.getWindow();
            if (window == null || window.getDecorView() == null) {
                return;
            }
            StringBuilder covers = new StringBuilder();
            int found = 0;
            java.util.HashSet<View> above = new java.util.HashSet<>();
            for (View v = page; v != null; ) {
                above.add(v);
                android.view.ViewParent parent = v.getParent();
                v = parent instanceof View ? (View) parent : null;
            }
            found = findCovers(window.getDecorView(), page, pageRect, above, covers, found);
            if (found > 0) {
                SlothLog.note("over the page (" + when + "): " + covers);
            }
        } catch (Throwable t) {
            SlothLog.note("cover probe failed: " + t.getClass().getSimpleName());
        }
    }

    /**
     * Walk the window looking for visible views that sit over most of the page.
     *
     * <p>A view that contains the page is not over it - the root of the window covers the
     * whole screen by definition, and reporting it would be noise. Only something the page
     * does not belong to can be in the way.</p>
     */
    private static int findCovers(View view, View page, Rect pageRect, java.util.Set<View> contains,
                                  StringBuilder out, int found) {
        if (view == page || found >= 4 || insideShell(view)) {
            return found;
        }
        boolean isAncestor = contains.contains(view);
        if (!isAncestor && view.getVisibility() == View.VISIBLE && view.getAlpha() > 0.05f) {
            Rect r = rectOf(view);
            if (r != null) {
                // a panel pushed off the screen is not covering anything, however large its
                // box is: judge where it is drawn, not where it was laid out
                r.offset(Math.round(view.getTranslationX()), Math.round(view.getTranslationY()));
                int overlap = overlapArea(r, pageRect);
                int own = Math.max(1, pageRect.width() * pageRect.height());
                if (overlap * 100 / own >= 60) {
                    if (out.length() > 0) {
                        out.append("; ");
                    }
                    out.append(simpleName(view)).append(tagOf(view))
                            .append(' ').append(overlap * 100 / own).append('%')
                            .append(" a=").append(Math.round(view.getAlpha() * 100) / 100f)
                            .append(view.getBackground() == null ? " bg=none" : " bg=yes")
                            .append(r.top >= pageRect.bottom ? " (below)" : "")
                            .append(r.bottom <= pageRect.top ? " (above)" : "");
                    found++;
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                found = findCovers(group.getChildAt(i), page, pageRect, contains, out, found);
            }
        }
        return found;
    }

    /** Anything of ours: the shell's own surface can never be what covers the page. */
    private static boolean insideShell(View view) {
        for (View v = view; v != null; ) {
            if ("shell-root".equals(v.getTag())) {
                return true;
            }
            android.view.ViewParent parent = v.getParent();
            v = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private static String simpleName(View view) {
        String name = view.getClass().getName();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }

    private static String tagOf(View view) {
        Object tag = view.getTag();
        return tag == null ? "" : "[" + tag + "]";
    }

    /** Where a view is on screen, or null when it is not laid out at all. */
    private static Rect rectOf(View view) {
        if (view.getWidth() <= 0 || view.getHeight() <= 0) {
            return null;
        }
        int[] at = new int[2];
        view.getLocationOnScreen(at);
        return new Rect(at[0], at[1], at[0] + view.getWidth(), at[1] + view.getHeight());
    }

    private static int overlapArea(Rect a, Rect b) {
        int left = Math.max(a.left, b.left);
        int top = Math.max(a.top, b.top);
        int right = Math.min(a.right, b.right);
        int bottom = Math.min(a.bottom, b.bottom);
        if (right <= left || bottom <= top) {
            return 0;
        }
        return (right - left) * (bottom - top);
    }
}
