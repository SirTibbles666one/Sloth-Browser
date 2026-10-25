package com.sloth.browser;

import android.app.Activity;

/**
 * The browser's own settings model, read and written by field name.
 *
 * <p>The classes are the app's, reached by reflection for the same reason every other
 * private thing in this shell is: they are not part of any interface we can compile
 * against. The field names are the ones in the shipped dex.</p>
 *
 * <p>A field is looked for up the class chain, not only on the exact class: engine
 * settings are the kind of thing an app subclasses, and getDeclaredField alone reports a
 * plain NoSuchFieldException for an inherited field - which reads to the person as a
 * switch that will not move. Values are written by their own type for the same reason:
 * setInt on a long throws, and the row would silently refuse.</p>
 */
final class SlothPrefs {

    /** Fields already reported, so a missing one does not fill the log with itself. */
    private static final java.util.HashSet<String> REPORTED = new java.util.HashSet<>();

    private SlothPrefs() {
    }

    private static Object model(Activity activity) {
        return SlothShell.settingsModel(activity);
    }

    static boolean has(Activity activity) {
        return model(activity) != null;
    }

    private static java.lang.reflect.Field field(Object model, String name)
            throws NoSuchFieldException {
        for (Class<?> type = model.getClass(); type != null; type = type.getSuperclass()) {
            try {
                java.lang.reflect.Field f = type.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                // an inherited field lives further up
            }
        }
        throw new NoSuchFieldException(name);
    }

    /** Report a problem with one field once, not on every refresh. */
    private static void report(String what, String field, Throwable t) {
        synchronized (REPORTED) {
            if (!REPORTED.add(what + field)) {
                return;
            }
        }
        SlothLog.note(what + field + ": " + t.getClass().getSimpleName());
    }

    static boolean flag(Activity activity, String field, boolean fallback) {
        Object s = model(activity);
        if (s == null) {
            return fallback;
        }
        try {
            java.lang.reflect.Field f = field(s, field);
            Class<?> type = f.getType();
            if (type == boolean.class || type == Boolean.class) {
                return f.getBoolean(s);
            }
            if (type == int.class || type == Integer.class) {
                return f.getInt(s) != 0;
            }
            // a field that is not a switch is not a switch: fall back rather than throw
            report("not a switch: ", field, new IllegalArgumentException(type.getName()));
            return fallback;
        } catch (Throwable t) {
            report("reading ", field, t);
            return fallback;
        }
    }

    static int number(Activity activity, String field, int fallback) {
        Object s = model(activity);
        if (s == null) {
            return fallback;
        }
        try {
            java.lang.reflect.Field f = field(s, field);
            Class<?> type = f.getType();
            if (type == int.class || type == Integer.class) {
                return f.getInt(s);
            }
            if (type == long.class || type == Long.class) {
                return (int) f.getLong(s);
            }
            if (type == float.class || type == Float.class) {
                return Math.round(f.getFloat(s));
            }
            report("not a number: ", field, new IllegalArgumentException(type.getName()));
            return fallback;
        } catch (Throwable t) {
            report("reading ", field, t);
            return fallback;
        }
    }

    /** Write one value and ask the app to store it. Returns false if either step failed. */
    static boolean write(Activity activity, String field, Object value) {
        Object s = model(activity);
        if (s == null) {
            SlothLog.note("no settings model to write " + field + " into");
            return false;
        }
        try {
            java.lang.reflect.Field f = field(s, field);
            Class<?> type = f.getType();
            if (value instanceof Boolean && (type == boolean.class || type == Boolean.class)) {
                f.setBoolean(s, (Boolean) value);
            } else if (value instanceof Integer && (type == int.class || type == Integer.class)) {
                f.setInt(s, (Integer) value);
            } else if (value instanceof Integer && (type == long.class || type == Long.class)) {
                f.setLong(s, ((Integer) value).longValue());
            } else if (value instanceof Integer && (type == float.class || type == Float.class)) {
                f.setFloat(s, ((Integer) value).floatValue());
            } else if (value instanceof String && type == String.class) {
                f.set(s, value);
            } else {
                report("wrong kind for ", field,
                        new IllegalArgumentException(type.getName()));
                return false;
            }
            return SlothShell.saveSettings(activity);
        } catch (Throwable t) {
            report("writing ", field, t);
            return false;
        }
    }
}
