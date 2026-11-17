package com.sloth.browser;

import android.app.Activity;

/**
 * The page's loading progress, read from the engine itself.
 *
 * <p>GeckoView reports a load through {@code GeckoSession.ProgressDelegate}: a start, progress as a
 * percentage, and a stop. The app may already have a delegate on the session, so ours is a proxy
 * that tells the bar and then forwards every call to the app's, unchanged - the app keeps whatever
 * it did with those calls. Nothing here is reached by name from the app's own classes, only from
 * GeckoView's public API.</p>
 *
 * <p>The session changes when the tab does, and the app may set a delegate of its own later; the
 * shell's tick calls {@link #watch} so the current session always carries the wrapper, and a
 * wrapper never wraps another wrapper.</p>
 */
final class SlothProgress {

    /** What the bar is told. Called on the main thread. */
    interface Listener {
        void started();

        void progress(int percent);

        void stopped();
    }

    private static final String DELEGATE = "org.mozilla.geckoview.GeckoSession$ProgressDelegate";

    /** The session currently wrapped, and the wrapper on it. */
    private static Object watched;
    private static Object wrapper;
    private static boolean loading;

    private SlothProgress() {
    }

    static boolean loading() {
        return loading;
    }

    /** Let go of the session, for a released shell: a GeckoSession outlives no window of ours. */
    static void forget() {
        watched = null;
        wrapper = null;
        loading = false;
    }

    /**
     * Make sure the page in this window reports its progress to {@code listener}.
     *
     * @return true when the current session carries our wrapper
     */
    static boolean watch(Activity activity, final Listener listener) {
        final Object session;
        try {
            session = SlothFind.session(activity);
        } catch (Throwable noEngine) {
            return false;
        }
        try {
            Object current = SlothShell.call(session, "getProgressDelegate", new Class<?>[0], new Object[0]);
            if (session == watched && current == wrapper && wrapper != null) {
                return true;
            }
            if (session != watched) {
                // a different tab: whatever was loading belonged to the last one
                setLoading(listener, false);
            }
            Class<?> type = Class.forName(DELEGATE, false, session.getClass().getClassLoader());
            final Object forwardTo = current;
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(),
                    new Class<?>[]{type}, new java.lang.reflect.InvocationHandler() {
                        @Override
                        public Object invoke(Object self, java.lang.reflect.Method method,
                                             Object[] args) throws Throwable {
                            String name = method.getName();
                            if (method.getDeclaringClass() == Object.class) {
                                if ("equals".equals(name)) {
                                    return self == args[0];
                                }
                                if ("hashCode".equals(name)) {
                                    return System.identityHashCode(self);
                                }
                                return "SlothProgress wrapper";
                            }
                            // only the session this wrapper was made for moves the bar
                            if (args != null && args.length > 0 && args[0] == session
                                    && session == watched) {
                                report(listener, name, args);
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
            watched = session;
            wrapper = proxy;
            SlothLog.note(forwardTo == null ? "watching page progress"
                    : "watching page progress beside the browser's own");
            return true;
        } catch (Throwable t) {
            SlothLog.warn("page progress unavailable: " + t.getClass().getSimpleName());
            return false;
        }
    }

    private static void report(final Listener listener, String name, Object[] args) {
        if ("onPageStart".equals(name)) {
            setLoading(listener, true);
            post(new Runnable() {
                @Override
                public void run() {
                    listener.progress(0);
                }
            });
        } else if ("onProgressChange".equals(name) && args.length > 1 && args[1] instanceof Integer) {
            final int percent = Math.max(0, Math.min(100, (Integer) args[1]));
            if (!loading && percent < 100) {
                setLoading(listener, true);
            }
            post(new Runnable() {
                @Override
                public void run() {
                    listener.progress(percent);
                }
            });
        } else if ("onPageStop".equals(name)) {
            setLoading(listener, false);
        }
    }

    private static void setLoading(final Listener listener, final boolean now) {
        if (loading == now) {
            return;
        }
        loading = now;
        post(new Runnable() {
            @Override
            public void run() {
                if (now) {
                    listener.started();
                } else {
                    listener.stopped();
                }
            }
        });
    }

    private static void post(Runnable work) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            work.run();
        } else {
            SlothShell.post(work);
        }
    }

    /** Stop the page loading, through the engine. False when there is no engine to ask. */
    static boolean stop(Activity activity) {
        try {
            SlothShell.call(SlothFind.session(activity), "stop", new Class<?>[0], new Object[0]);
            SlothLog.note("stopped the page");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
