package com.sloth.browser;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/**
 * A small ring of things that went wrong, kept where a person can see them.
 *
 * <p>The shell talks to the browser by reflection, by name and by tag. When one of those
 * lookups misses, the honest failure is silence - a button that does nothing - and the
 * only evidence was whatever the person saw. This keeps the last few misses on the phone
 * so a report can say what actually happened instead of what it looked like.</p>
 *
 * <p>Kept in storage as well as in memory: a fault that ends the session is exactly the
 * fault worth reading afterwards, and a log that empties itself when the app restarts
 * answers nothing.</p>
 */
final class SlothLog {

    private static final int KEEP = 120;
    private static final String STORE = "slothworks";
    private static final String KEY = "problems";
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private static SharedPreferences store;
    private static boolean loaded;

    private SlothLog() {
    }

    /** Point the log at this app's storage and read back what the last session saw. */
    static void attach(Context context) {
        if (context == null) {
            return;
        }
        try {
            // the storage reference follows the context; only the reading happens once,
            // so a shell built into a new window still writes to the same app storage
            store = context.getApplicationContext()
                    .getSharedPreferences(STORE, Context.MODE_PRIVATE);
            if (loaded) {
                return;
            }
            String kept = store.getString(KEY, null);
            loaded = true;
            if (kept != null && !kept.isEmpty()) {
                synchronized (LINES) {
                    LINES.clear();
                    for (String line : kept.split("\n")) {
                        if (!line.isEmpty()) {
                            LINES.addLast(line);
                        }
                    }
                    while (LINES.size() > KEEP) {
                        LINES.removeFirst();
                    }
                }
            }
        } catch (Throwable ignored) {
            loaded = true;
        }
    }

    /** A fault the person needs to see: the shell could not do what was asked. */
    static void error(String what) {
        note("ERROR " + what);
    }

    /** Something worked around, or would have been wrong if left alone. */
    static void warn(String what) {
        note("WARN " + what);
    }

    static void note(String what) {
        try {
            String line = TIME.format(new Date()) + "  " + what;
            synchronized (LINES) {
                while (LINES.size() >= KEEP) {
                    LINES.removeFirst();
                }
                LINES.addLast(line);
                persistLocked();
            }
            android.util.Log.d("SlothShell", what);
        } catch (Throwable ignored) {
        }
    }

    private static void persistLocked() {
        if (store == null) {
            return;
        }
        StringBuilder out = new StringBuilder();
        for (String line : LINES) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(line);
        }
        // committed, not applied: a fault that ends the session is what this log is for,
        // and an asynchronous write can be lost with the process that queued it
        store.edit().putString(KEY, out.toString()).commit();
    }

    /**
     * Install a last word for a crash.
     *
     * <p>A black page and a dead window leave nothing to look at afterwards. This records
     * the exception and its first frames into the same ring the Settings page shows, and
     * writes it to a file, before the app is allowed to die. Only the first crash of a
     * session is captured, so a loop cannot overwrite the cause with its repeats.</p>
     */
    static void installCrash(final Context context) {
        if (crashInstalled) {
            return;
        }
        crashInstalled = true;
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable fault) {
                try {
                    StringBuilder line = new StringBuilder("CRASH on ").append(thread.getName())
                            .append(": ").append(fault.getClass().getName());
                    String message = fault.getMessage();
                    if (message != null) {
                        line.append(": ").append(message);
                    }
                    StackTraceElement[] frames = fault.getStackTrace();
                    for (int i = 0; i < frames.length && i < 6; i++) {
                        line.append(" | ").append(frames[i].toString());
                    }
                    note(line.toString());
                    save(context);
                } catch (Throwable ignored) {
                    // a crash handler that crashes is worse than none
                }
                if (previous != null) {
                    previous.uncaughtException(thread, fault);
                }
            }
        });
    }

    private static boolean crashInstalled;

    /** Write the ring where a report can be read after the app is gone. */
    static String save(Context context) {
        if (context == null) {
            return null;
        }
        try {
            java.io.File file = new java.io.File(context.getApplicationContext().getFilesDir(),
                    "diagnostics.txt");
            java.io.FileOutputStream out = new java.io.FileOutputStream(file, false);
            try {
                out.write(text().getBytes("UTF-8"));
            } finally {
                out.close();
            }
            return file.getAbsolutePath();
        } catch (Throwable t) {
            return null;
        }
    }

    static int count() {
        synchronized (LINES) {
            return LINES.size();
        }
    }

    static String text() {
        StringBuilder out = new StringBuilder();
        synchronized (LINES) {
            for (String line : LINES) {
                if (out.length() > 0) {
                    out.append('\n');
                }
                out.append(line);
            }
        }
        return out.length() == 0
                ? "Nothing has gone wrong since the app was installed."
                : out.toString();
    }

    static void clear() {
        synchronized (LINES) {
            LINES.clear();
            persistLocked();
        }
    }
}
