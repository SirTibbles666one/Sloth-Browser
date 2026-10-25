package com.sloth.browser;

import android.content.pm.Signature;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;

/**
 * Small, self-contained corrections for the surfaces audited in build 1096.
 */
public final class SlothUi {

    private static final int ICON_CAP = 120;

    private SlothUi() {
    }

    /** ThemeFace.disks recoloured shared ConstantState without mutate(): the skin bled. */
    public static void disks(ViewGroup group, int fill, int stroke, int width) {
        if (group == null) {
            return;
        }
        Method address = null;
        try {
            address = Class.forName("com.sloth.browser.ThemeFace")
                    .getDeclaredMethod("address", View.class);
            address.setAccessible(true);
        } catch (Throwable ignored) {
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            boolean isAddress = false;
            if (address != null) {
                try {
                    isAddress = Boolean.TRUE.equals(address.invoke(null, child));
                } catch (Throwable ignored) {
                }
            }
            if (isAddress) {
                child.setForeground(null);
                child.setBackground(null);
                child.setFocusable(false);
                child.setDefaultFocusHighlightEnabled(false);
            } else {
                Drawable background = child.getBackground();
                if (background instanceof GradientDrawable) {
                    try {
                        Drawable mutated = background.mutate();
                        ((GradientDrawable) mutated).setColor(fill);
                        ((GradientDrawable) mutated).setStroke(width, stroke);
                        if (mutated != background) {
                            child.setBackground(mutated);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }


    /**
     * SquareFace.TICK re-posts itself every 20 seconds with no stop condition, and
     * its statics keep pointing at the last Activity's views. Stop the chain once
     * that Activity is gone; the next build of the clock tile posts it again.
     * Fails open (keeps ticking) so a reflection hiccup can never freeze the clock.
     */
    public static boolean canTick() {
        try {
            Class<?> square = Class.forName("com.sloth.browser.SquareFace");
            java.lang.reflect.Field face = square.getDeclaredField("clockFace");
            face.setAccessible(true);
            Object clock = face.get(null);
            java.lang.reflect.Field hostField = square.getDeclaredField("host");
            hostField.setAccessible(true);
            Object host = hostField.get(null);
            if (clock == null || host == null) {
                return false;
            }
            return !(host instanceof android.app.Activity) || !((android.app.Activity) host).isDestroyed();
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** GitHubUpdate.trusted compared only the first signer; compare the whole list. */
    public static boolean sameSigners(Signature[] mine, Signature[] theirs) {
        if (mine == null || theirs == null || mine.length == 0 || theirs.length == 0) {
            return false;
        }
        if (mine.length != theirs.length) {
            return false;
        }
        for (int i = 0; i < mine.length; i++) {
            Signature a = mine[i];
            Signature b = theirs[i];
            if (a == null || b == null || !a.equals(b)) {
                return false;
            }
        }
        return true;
    }

    /** TabSquares kept a favicon per host forever; cap the map instead. */
    public static void remember(HashMap map, Object key, Object value) {
        if (map == null || value == null) {
            return;
        }
        synchronized (map) {
            if (map.size() >= ICON_CAP && !map.containsKey(key)) {
                map.clear();
            }
            map.put(key, value);
        }
    }

    /** GitHubUpdate never cleaned getCacheDir()/updates: one 108 MB APK per version. */
    public static boolean updates(File dir, String keep) {
        if (dir == null) {
            return false;
        }
        boolean made = dir.isDirectory() || dir.mkdirs();
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file == null || !file.isFile()) {
                        continue;
                    }
                    String name = file.getName();
                    boolean partial = name.endsWith(".part");
                    boolean staleApk = name.endsWith(".apk") && (keep == null || !keep.equals(name));
                    if (partial || staleApk) {
                        try {
                            file.delete();
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        }
        return made || dir.isDirectory();
    }

    /**
     * Index of the session that belongs to the visible tab.
     *
     * UBlockFace.open() used sessions.get(size - 1) - the newest session - so the
     * ad-block dashboard loaded in whichever tab was created last, which is
     * usually a background tab: tapping the dashboard appeared to do nothing.
     * Prefer the Activity's own `active` index, clamped to the list.
     */
    public static int pick(android.app.Activity activity, int size) {
        if (size <= 0) {
            return 0;
        }
        try {
            java.lang.reflect.Field f = activity.getClass().getDeclaredField("active");
            f.setAccessible(true);
            int active = f.getInt(activity);
            if (active >= 0 && active < size) {
                return active;
            }
        } catch (Throwable ignored) {
        }
        return size - 1;
    }

    /**
     * Drop map entries whose key is no longer a live tab.
     *
     * TabSquares keeps two static HashMaps keyed by BrowserTab (url history and
     * the stale-title note) and never removed anything on close, so every closed
     * tab stayed reachable - its session and views included - for the life of the
     * process. Called from TabSquares.note() with the live tab list.
     */
    public static void prune(java.util.Map map, java.util.List live) {
        if (map == null || live == null) {
            return;
        }
        try {
            java.util.Iterator it = map.keySet().iterator();
            while (it.hasNext()) {
                Object key = it.next();
                if (!live.contains(key)) {
                    it.remove();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static int squareHost;

    /**
     * SquareFace caches the new-tab widgets in statics (clockFace, dateFace, the
     * task and link editors) and keys its "already built" memo on a static
     * lastStamp. Rotating the device hands place() a new Activity, but the memo
     * still matched, so place() returned early and every cached view kept
     * pointing at the destroyed Activity: the clock was painted into a dead
     * TextView (it froze on screen) and the old Activity could never be
     * collected. Drop the cached views and the memo whenever the Activity
     * changes, so the shelf is rebuilt for the live window.
     */
    public static void resetSquare(android.app.Activity activity) {
        if (activity == null) {
            return;
        }
        int id = System.identityHashCode(activity);
        if (id == squareHost) {
            return;
        }
        squareHost = id;
        try {
            Class<?> c = Class.forName("com.sloth.browser.SquareFace");
            String[] views = {"clockFace", "column", "dateFace", "fromLabel", "grid",
                    "laterRows", "laterStatus", "linkNameField", "linkRows", "linkStatus",
                    "linkUrlField", "taskField"};
            for (int i = 0; i < views.length; i++) {
                try {
                    java.lang.reflect.Field f = c.getDeclaredField(views[i]);
                    f.setAccessible(true);
                    f.set(null, null);
                } catch (Throwable ignored) {
                }
            }
            try {
                java.lang.reflect.Field stamp = c.getDeclaredField("lastStamp");
                stamp.setAccessible(true);
                stamp.set(null, "");
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }
}
