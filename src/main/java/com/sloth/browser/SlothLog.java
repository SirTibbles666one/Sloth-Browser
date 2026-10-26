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
    /** Faults get their own room, so a chatty minute of notes cannot evict the cause. */
    private static final int KEEP_FAULTS = 20;
    private static final String STORE = "slothworks";
    private static final String KEY = "problems";
    private static final String KEY_FAULTS = "faults";
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static final ArrayDeque<String> FAULTS = new ArrayDeque<>();
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
            String faults = store.getString(KEY_FAULTS, null);
            loaded = true;
            synchronized (LINES) {
                LINES.clear();
                FAULTS.clear();
                fill(LINES, kept, KEEP);
                fill(FAULTS, faults, KEEP_FAULTS);
            }
        } catch (Throwable ignored) {
            loaded = true;
        }
    }

    /** A fault the person needs to see: the shell could not do what was asked. */
    static void error(String what) {
        record("ERROR ", what, true);
    }

    /** Something worked around, or would have been wrong if left alone. */
    static void warn(String what) {
        record("WARN ", what, false);
    }

    static void note(String what) {
        record("", what, false);
    }

    /**
     * Add a line, and store it only as expensively as it deserves.
     *
     * <p>Every note used to be a synchronous commit to storage on the main thread, and the
     * probe and the tick can emit several a second: a disk write per line while the UI is
     * drawing. Notes go through apply(); an error commits, because that is the line whose
     * loss would leave a report with nothing in it.</p>
     */
    private static void record(String prefix, String what, boolean durable) {
        try {
            String line = TIME.format(new Date()) + "  " + prefix + what;
            synchronized (LINES) {
                // a fault is worth more than any note, so it is kept in a ring of its own: the
                // 120-line ring below is shared with the probe and the tick, and a busy minute
                // used to push "CRASH on main: ..." off the top of the report
                if (durable) {
                    while (FAULTS.size() >= KEEP_FAULTS) {
                        FAULTS.removeFirst();
                    }
                    FAULTS.addLast(line);
                }
                while (LINES.size() >= KEEP) {
                    LINES.removeFirst();
                }
                LINES.addLast(line);
                persistLocked(durable);
            }
            android.util.Log.d("SlothShell", what);
        } catch (Throwable ignored) {
        }
    }

    private static void persistLocked() {
        persistLocked(true);
    }

    private static void persistLocked(boolean durable) {
        if (store == null) {
            return;
        }
        String notes = join(LINES);
        String faults = join(FAULTS);
        // a fault that ends the session is what this log is for, and an asynchronous write
        // can be lost with the process that queued it - so errors commit, notes do not
        if (durable) {
            store.edit().putString(KEY, notes).putString(KEY_FAULTS, faults).commit();
        } else {
            store.edit().putString(KEY, notes).putString(KEY_FAULTS, faults).apply();
        }
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

    /** Everything kept: faults and notes together, because both are lines a report shows. */
    static int count() {
        synchronized (LINES) {
            return LINES.size() + FAULTS.size();
        }
    }

    /** Faults first, then the running notes: the reason before the circumstances. */
    static String text() {
        String faults;
        String notes;
        synchronized (LINES) {
            faults = join(FAULTS);
            notes = join(LINES);
        }
        if (faults.length() == 0 && notes.length() == 0) {
            return "Nothing has gone wrong since the app was installed.";
        }
        StringBuilder out = new StringBuilder();
        if (faults.length() > 0) {
            out.append("FAULTS\n").append(faults);
        }
        if (notes.length() > 0) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append("RECENT\n").append(notes);
        }
        return out.toString();
    }

    /** The faults alone, for a report that only wants the reasons. */
    static String faults() {
        synchronized (LINES) {
            return join(FAULTS);
        }
    }

    /** Restore one ring, ignoring the section headings a saved report carries. */
    private static void fill(ArrayDeque<String> ring, String stored, int keep) {
        if (stored == null || stored.isEmpty()) {
            return;
        }
        for (String line : stored.split("\n")) {
            if (line.isEmpty() || "FAULTS".equals(line) || "RECENT".equals(line)) {
                continue;
            }
            ring.addLast(line);
        }
        while (ring.size() > keep) {
            ring.removeFirst();
        }
    }

    private static String join(java.util.Collection<String> lines) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(line);
        }
        return out.toString();
    }

    static void clear() {
        synchronized (LINES) {
            LINES.clear();
            FAULTS.clear();
            persistLocked();
        }
    }
}
