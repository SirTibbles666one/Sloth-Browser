package com.sloth.browser;

import android.app.Activity;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Page loads, per session, from the engine's own progress delegate.
 *
 * <p>GeckoView reports a load through {@code GeckoSession.ProgressDelegate}: a start with the
 * page's URL, progress as a percentage, and a stop. The app may already have a delegate on the
 * session, so ours is a proxy that records the load and then forwards every call to the app's,
 * unchanged. Nothing here is reached by name from the app's own classes, only from GeckoView's
 * public API.</p>
 *
 * <p>Every session the shell has seen keeps its own state, so a tab switch shows that tab's load
 * and not the one left behind, and a tab loading in the background is recorded without moving
 * the bar. The listener hears only about the session on screen. The shell's tick calls
 * {@link #watch} so a new session, or a delegate the app sets later, is wrapped; a wrapper is
 * never wrapped again.</p>
 */
final class SlothLoad {

    /** Told, on the main thread, that the session on screen changed or its load did. */
    interface Listener {
        void changed(Object session);
    }

    /** One session's load. Progress never runs backwards within a load. */
    static final class State {
        boolean loading;
        int progress;
        /** The URL the engine reported at the page's start, scheme included. */
        String url = "";
    }

    private static final String DELEGATE = "org.mozilla.geckoview.GeckoSession$ProgressDelegate";
    private static final String MARK = "SlothLoad wrapper";

    private static final Map<Object, State> STATES = new WeakHashMap<>();
    /** The wrapper each session carries, so the tick can tell ours from one the app set. */
    private static final Map<Object, Object> WRAPPERS = new WeakHashMap<>();
    private static WeakReference<Object> showing = new WeakReference<>(null);
    private static Listener listener;

    private SlothLoad() {
    }

    /** Let go of the listener and the session on screen, for a released shell. */
    static void forget() {
        listener = null;
        showing = new WeakReference<>(null);
    }

    /**
     * Make sure the page in this window reports its loads, and tell the listener when the
     * session on screen is a different one from last time.
     *
     * @return true when the session on screen carries our wrapper
     */
    static boolean watch(Activity activity, Listener next) {
        listener = next;
        Object session;
        try {
            session = SlothFind.session(activity);
        } catch (Throwable noEngine) {
            return false;
        }
        boolean wrapped = attach(session);
        if (session != null && session != showing.get()) {
            showing = new WeakReference<>(session);
            tell(session);
        }
        return wrapped;
    }

    /** Wrap this session's delegate, unless it already carries our wrapper. */
    static boolean attach(final Object session) {
        if (session == null) {
            return false;
        }
        try {
            Object current = SlothShell.call(session, "getProgressDelegate", new Class<?>[0], new Object[0]);
            synchronized (WRAPPERS) {
                if (current != null && current == WRAPPERS.get(session)) {
                    return true;
                }
            }
            if (current != null && MARK.equals(String.valueOf(current))) {
                // a wrapper from an earlier shell: it records into the same states
                synchronized (WRAPPERS) {
                    WRAPPERS.put(session, current);
                }
                return true;
            }
            Class<?> type = Class.forName(DELEGATE, false, session.getClass().getClassLoader());
            final Object forwardTo = current;
            final WeakReference<Object> owner = new WeakReference<>(session);
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(),
                    new Class<?>[]{type}, new java.lang.reflect.InvocationHandler() {
                        @Override
                        public Object invoke(Object self, java.lang.reflect.Method method,
                                             Object[] args) throws Throwable {
                            String name = method.getName();
                            if (method.getDeclaringClass() == Object.class) {
                                if ("equals".equals(name)) {
                                    return args != null && self == args[0];
                                }
                                if ("hashCode".equals(name)) {
                                    return System.identityHashCode(self);
                                }
                                return MARK;
                            }
                            Object mine = owner.get();
                            if (mine != null && args != null && args.length > 0 && args[0] == mine) {
                                record(mine, name, args);
                            }
                            if (forwardTo == null) {
                                return null;
                            }
                            try {
                                return method.invoke(forwardTo, args);
                            } catch (java.lang.reflect.InvocationTargetException wrapped) {
                                throw wrapped.getCause();
                            }
                        }
                    });
            SlothShell.call(session, "setProgressDelegate", new Class<?>[]{type}, new Object[]{proxy});
            synchronized (WRAPPERS) {
                WRAPPERS.put(session, proxy);
            }
            SlothLog.note(forwardTo == null ? "watching page loads"
                    : "watching page loads beside the browser's own");
            return true;
        } catch (Throwable t) {
            SlothLog.warn("page loads unavailable: " + t.getClass().getSimpleName());
            return false;
        }
    }

    /** This session's load. Never null: a session not seen yet is at rest. */
    static State state(Object session) {
        synchronized (STATES) {
            State state = session == null ? null : STATES.get(session);
            if (state == null) {
                state = new State();
                if (session != null) {
                    STATES.put(session, state);
                }
            }
            return state;
        }
    }

    /** The URL the engine reported for this session's page, scheme included, or empty. */
    static String pageUrl(Object session) {
        return session == null ? "" : state(session).url;
    }

    /** The session on screen, as last seen by {@link #watch}. */
    static Object showing() {
        return showing.get();
    }

    /** Stop the page on screen, through the engine. False when there is no engine to ask. */
    static boolean stop(Activity activity) {
        try {
            Object session = SlothFind.session(activity);
            SlothShell.call(session, "stop", new Class<?>[0], new Object[0]);
            // the engine reports its own stop too; the bar should not wait for it
            state(session).loading = false;
            tell(session);
            SlothLog.note("stopped the page");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void record(Object session, String name, Object[] args) {
        State state = state(session);
        if ("onPageStart".equals(name)) {
            state.loading = true;
            state.progress = 0;
            if (args.length > 1 && args[1] instanceof String) {
                state.url = (String) args[1];
            }
        } else if ("onProgressChange".equals(name) && args.length > 1 && args[1] instanceof Number) {
            int percent = Math.max(0, Math.min(100, ((Number) args[1]).intValue()));
            if (!state.loading && percent < 100) {
                // a load can report progress before, or without, its start
                state.loading = true;
                state.progress = 0;
            }
            state.progress = Math.max(state.progress, percent);
        } else if ("onPageStop".equals(name)) {
            state.loading = false;
            state.progress = 100;
        } else {
            return;
        }
        if (session == showing.get()) {
            tell(session);
        }
    }

    private static void tell(final Object session) {
        final Listener now = listener;
        if (now == null) {
            return;
        }
        Runnable work = new Runnable() {
            @Override
            public void run() {
                now.changed(session);
            }
        };
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            work.run();
        } else {
            SlothShell.post(work);
        }
    }
}
