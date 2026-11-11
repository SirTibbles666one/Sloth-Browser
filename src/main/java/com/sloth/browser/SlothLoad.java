package com.sloth.browser;

import android.app.Activity;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Page load, per session, from the engine's own progress delegate.
 *
 * <p>The shell cannot see a page starting or finishing unless something tells it. GeckoSession
 * already has that something: {@code ProgressDelegate}. This installs one that reports here and
 * then calls whatever delegate the app had installed, so the app's own handling still runs.
 * Each session keeps its own progress, which is how a tab switch shows that tab's load and not
 * the one left behind.</p>
 */
final class SlothLoad {

    /** Told on the main thread after a session's shown progress changes. */
    interface Listener {
        void changed();
    }

    private static final Map<Object, int[]> PROGRESS = new WeakHashMap<>();
    /** -1 means the line is hidden. Otherwise 0 to 100. */
    private static final int HIDDEN = -1;
    private static Listener listener;

    private SlothLoad() {
    }

    static void setListener(Listener next) {
        listener = next;
    }

    /** Attach to the session in this window, if it has a progress delegate and we have not. */
    static void watch(Activity activity) {
        try {
            Object session = SlothFind.session(activity);
            attach(session);
        } catch (Throwable ignored) {
            // no engine view yet, or this build's session has no progress delegate
        }
    }

    static void attach(Object session) {
        if (session == null) {
            return;
        }
        try {
            Class<?> face = Class.forName("org.mozilla.geckoview.GeckoSession$ProgressDelegate");
            Method get = session.getClass().getMethod("getProgressDelegate");
            Method set = session.getClass().getMethod("setProgressDelegate", face);
            Object current = get.invoke(session);
            if (current != null && "SlothLoad".equals(String.valueOf(current))) {
                return;
            }
            final Object previous = current;
            final Object key = session;
            Object proxy = Proxy.newProxyInstance(face.getClassLoader(), new Class<?>[]{face},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object p, Method method, Object[] args) throws Throwable {
                            String name = method.getName();
                            if ("onPageStart".equals(name)) {
                                note(key, 8, true);
                            } else if ("onProgressChange".equals(name) && args != null
                                    && args.length >= 2 && args[1] instanceof Number) {
                                note(key, ((Number) args[1]).intValue(), true);
                            } else if ("onPageStop".equals(name)) {
                                note(key, 100, false);
                            } else if ("hashCode".equals(name)) {
                                return System.identityHashCode(p);
                            } else if ("equals".equals(name)) {
                                return args != null && args.length == 1 && p == args[0];
                            } else if ("toString".equals(name)) {
                                return "SlothLoad";
                            }
                            if (previous != null && !"hashCode".equals(name)
                                    && !"equals".equals(name) && !"toString".equals(name)) {
                                return method.invoke(previous, args);
                            }
                            return null;
                        }
                    });
            set.invoke(session, proxy);
        } catch (Throwable ignored) {
            // a session without this delegate still loads; it just has no bar
        }
    }

    /** The line for this session: -1 hidden, otherwise 0 to 100. */
    static int shown(Object session) {
        if (session == null) {
            return HIDDEN;
        }
        int[] state;
        synchronized (PROGRESS) {
            state = PROGRESS.get(session);
        }
        if (state == null || state[1] == 0) {
            return HIDDEN;
        }
        return state[0];
    }

    private static void note(final Object session, int progress, boolean loading) {
        int value = Math.max(0, Math.min(100, progress));
        synchronized (PROGRESS) {
            // [progress, loading flag]
            PROGRESS.put(session, new int[]{value, loading ? 1 : 0});
        }
        final Listener watch = listener;
        if (watch == null) {
            return;
        }
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        final WeakReference<Object> ref = new WeakReference<>(session);
        main.post(new Runnable() {
            @Override
            public void run() {
                if (ref.get() != null) {
                    watch.changed();
                }
            }
        });
    }
}
