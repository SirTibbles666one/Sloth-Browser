package com.sloth.browser;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Drawer corrections for build 1091.
 *
 * 1091's shortPx() adds the drawer header height to the tab-squares bottom. The
 * squares already sit below the header, so their bottom includes it - the sum
 * counts the header twice and the drawer rests ~56dp too low, leaving an empty
 * band under the tiles.
 *
 * 1091's DrawerFit.resting() returns 0 when the content has not been measured
 * yet, and snap() then treats 0 as a candidate target, so a let-go on a drawer
 * that has not measured closes it instead of resting. resting() also lets the
 * rest height fall below the close threshold.
 *
 * This replaces all three answers: the rest height is max(header, squares), a
 * zero measurement falls back to the compact floor, and a let-go only closes
 * when it started below the close threshold.
 */
public final class SlothDrawer {

    private SlothDrawer() {
    }


    private static final java.lang.ref.WeakReference<?>[] claimed = new java.lang.ref.WeakReference<?>[2];

    /**
     * Per-activity one-shot latch. watch() and ownDrag() each used a plain static
     * boolean that was never cleared, so both installed their hook on the first
     * Activity only: after any recreate the drawer stopped re-fitting and the dock
     * drag died. Returns true when this activity still needs the hook.
     */
    public static synchronized boolean claim(int slot, Activity activity) {
        if (activity == null || slot < 0 || slot >= claimed.length) {
            return false;
        }
        java.lang.ref.WeakReference<?> ref = claimed[slot];
        if (ref != null && ref.get() == activity) {
            return false;
        }
        claimed[slot] = new java.lang.ref.WeakReference<Activity>(activity);
        return true;
    }

    public static int resting(int content, int min, int sheetMax) {
        if (sheetMax < 0) {
            sheetMax = 0;
        }
        int floor = Math.max(0, Math.min(min, sheetMax));
        if (content <= 0) {
            return floor;
        }
        return Math.max(floor, Math.min(content, sheetMax));
    }

    public static int snap(int current, int rest, int sheetMax, int[] rows, int slop, int closeBelow) {
        if (sheetMax < 0) {
            sheetMax = 0;
        }
        if (current < closeBelow) {
            return 0;
        }
        int restHeight = resting(rest, closeBelow, sheetMax);
        int best = restHeight;
        int bestDelta = Math.abs(current - restHeight);

        if (rows != null) {
            for (int row : rows) {
                if (row < restHeight || row > sheetMax) {
                    continue;
                }
                int delta = Math.abs(current - row);
                if (delta < bestDelta) {
                    best = row;
                    bestDelta = delta;
                }
            }
        }

        int fullDelta = Math.abs(current - sheetMax);
        if (fullDelta <= slop && fullDelta <= bestDelta) {
            return sheetMax;
        }
        return best;
    }


    private static final java.util.WeakHashMap<View, Integer> held = new java.util.WeakHashMap<View, Integer>();

    /**
     * keepButtons() forced six bar buttons plus settings-pin VISIBLE whenever the
     * address was focused and never put them back, so a control the user had moved
     * to the drawer (GONE in the bar) reappeared in the bar after the first focus
     * and stayed there. Remember each one's own visibility and restore it on blur.
     */
    public static void buttons(View row) {
        if (row == null) {
            return;
        }
        try {
            android.content.Context context = row.getContext();
            if (!(context instanceof Activity)) {
                return;
            }
            Object binding = field(context, "binding");
            if (binding == null) {
                return;
            }
            View url = (View) field(binding, "urlBar");
            if (url == null) {
                return;
            }
            String[] names = {"btnBack", "btnFwd", "btnReload", "btnPlus", "btnMenu", "btnTabs"};
            View[] views = new View[names.length + 1];
            for (int i = 0; i < names.length; i++) {
                views[i] = (View) field(binding, names[i]);
            }
            views[names.length] = find(row, "settings-pin");

            if (url.isFocused()) {
                for (View view : views) {
                    if (view != null && view.getParent() != null && view.getVisibility() != View.VISIBLE) {
                        if (!held.containsKey(view)) {
                            held.put(view, view.getVisibility());
                        }
                        view.setVisibility(View.VISIBLE);
                    }
                }
            } else if (!held.isEmpty()) {
                for (java.util.Map.Entry<View, Integer> entry : held.entrySet()) {
                    View view = entry.getKey();
                    if (view != null) {
                        view.setVisibility(entry.getValue());
                    }
                }
                held.clear();
            }
        } catch (Throwable ignored) {
        }
    }

    private static View find(View root, String tag) {
        if (root == null) {
            return null;
        }
        if (tag.equals(String.valueOf(root.getTag()))) {
            return root;
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View hit = find(group.getChildAt(i), tag);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    /**
     * LockSheet remembers where it took pageInfo from in static fields with no
     * owner, so after a rotation it can re-parent the new Activity's pageInfo into
     * the destroyed Activity's hierarchy. Clear the saved slot when the Activity
     * changes.
     */
    public static void lockHome(Activity activity) {
        if (!claim(2, activity)) {
            return;
        }
        try {
            Class<?> sheet = Class.forName("com.sloth.browser.LockSheet");
            java.lang.reflect.Field home = sheet.getDeclaredField("home");
            home.setAccessible(true);
            home.set(null, null);
            java.lang.reflect.Field at = sheet.getDeclaredField("homeAt");
            at.setAccessible(true);
            at.setInt(null, -1);
            java.lang.reflect.Field top = sheet.getDeclaredField("homeTop");
            top.setAccessible(true);
            top.setInt(null, -1);
            try {
                java.lang.reflect.Field bottom = sheet.getDeclaredField("homeBottom");
                bottom.setAccessible(true);
                bottom.setInt(null, -1);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    public static int shortPx(Activity activity) {
        int fallback = dp(activity, 140);
        try {
            LinearLayout body = body(activity);
            if (body == null) {
                return fallback;
            }
            int header = 0;
            int squares = 0;
            for (int i = 0; i < body.getChildCount(); i++) {
                View child = body.getChildAt(i);
                String tag = String.valueOf(child.getTag());
                int bottom = child.getBottom() > 0
                        ? child.getBottom()
                        : child.getTop() + child.getMeasuredHeight();
                if ("drawer-head".equals(tag) && bottom > header) {
                    header = bottom;
                } else if ("tab-squares".equals(tag) && bottom > squares) {
                    squares = bottom;
                }
            }
            // the squares sit below the header, so their bottom already includes it
            int content = Math.max(header, squares);
            if (content <= 0) {
                return fallback;
            }
            return resting(content + body.getPaddingBottom(), fallback, sheetMax(activity));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static LinearLayout body(Activity activity) {
        try {
            Object binding = field(activity, "binding");
            if (binding == null) {
                return null;
            }
            Object grid = field(binding, "chromeWidgetGrid");
            if (!(grid instanceof View)) {
                return null;
            }
            android.view.ViewParent parent = ((View) grid).getParent();
            return parent instanceof LinearLayout ? (LinearLayout) parent : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int sheetMax(Activity activity) {
        try {
            Class<?> drawer = Class.forName("com.sloth.browser.DrawerFace");
            Method m = drawer.getDeclaredMethod("sheetMax", Activity.class);
            m.setAccessible(true);
            Object value = m.invoke(null, activity);
            return value instanceof Integer ? (Integer) value : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static Object field(Object target, String name) {
        for (Class<?> cls = target.getClass(); cls != null; cls = cls.getSuperclass()) {
            try {
                Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {
                // walk up
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
