package com.sloth.browser;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Bookmarks and history as lists in the drawer's material.
 *
 * <p>The browser's own panel is where those lists live, and the panel is the thing this shell
 * keeps shut because it was the empty box over the page. Lending it back works only when the
 * app draws into it in time, and when it does not the person is left looking at a blank page.
 * This page asks the engine for the same records instead - GeckoView's storage controller - and
 * draws them itself.</p>
 *
 * <p>Everything here is reflective on purpose: the shell is compiled against no GeckoView, and
 * every call is wrapped. A build where the bridge does not resolve shows a sentence and a log
 * line, never a crash and never a blank list pretending to be empty.</p>
 */
final class SlothLibrary {

    /** Where a tapped row goes. */
    interface Sink {
        void openUrl(String url);
    }

    private static SlothLibrary instance;

    private final Activity activity;
    private final Sink sink;
    private final TextView title;
    private final TextView note;
    private final LinearLayout list;
    private String kind = "bookmarks";

    private SlothLibrary(Activity activity, Sink sink, TextView title, TextView note,
                         LinearLayout list) {
        this.activity = activity;
        this.sink = sink;
        this.title = title;
        this.note = note;
        this.list = list;
    }

    static View build(final Activity activity, final Sink sink, final Runnable onClose) {
        FrameLayout page = new FrameLayout(activity);
        page.setTag("library-page");
        page.setBackgroundColor(SlothTheme.bg());

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dip(activity, 18), dip(activity, 14), dip(activity, 18), dip(activity, 18));

        LinearLayout head = new LinearLayout(activity);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, 0, 0, dip(activity, 10));

        TextView back = label(activity, "\u2039", 24f, SlothTheme.ink(), false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setBackground(pill(activity, SlothTheme.card(), SlothTheme.lineAt(0x24)));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClose.run();
            }
        });
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(dip(activity, 40), dip(activity, 40));
        head.addView(back, backLp);

        TextView title = label(activity, "Bookmarks", 22f, SlothTheme.ink(), true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = dip(activity, 12);
        head.addView(title, titleLp);
        column.addView(head);

        TextView note = label(activity, "", 12f, SlothTheme.muted(), false);
        note.setPadding(0, 0, 0, dip(activity, 10));
        column.addView(note);

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);

        ScrollView scroller = new ScrollView(activity);
        scroller.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        page.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        instance = new SlothLibrary(activity, sink, title, note, list);
        return page;
    }

    /** Fill the page for one kind and say whether there is a page to show at all. */
    static boolean show(String kind) {
        if (instance == null) {
            return false;
        }
        instance.fill(kind);
        return true;
    }

    private void fill(String kind) {
        this.kind = kind;
        title.setText("bookmarks".equals(kind) ? "Bookmarks" : "History");
        list.removeAllViews();
        note.setText("Asking the browser for its list\u2026");
        note.setVisibility(View.VISIBLE);
        try {
            Object runtime = runtimeOf(activity);
            if (runtime == null) {
                throw new IllegalStateException("no engine view in this window");
            }
            Object storage = call(runtime, "getStorageController");
            Object store = call(storage, "bookmarks".equals(kind) ? "getBookmarks" : "getHistory");
            Object result = listOn(store);
            accept(result);
        } catch (Throwable t) {
            unavailable("the browser's own list", t);
        }
    }

    private void unavailable(String what, Throwable why) {
        String reason = why.getClass().getSimpleName()
                + (why.getMessage() == null ? "" : ": " + why.getMessage());
        SlothLog.note("library: " + kind + " unavailable (" + reason + ")");
        list.removeAllViews();
        note.setVisibility(View.VISIBLE);
        note.setText(what + " did not answer. Diagnostics has the reason.");
    }

    /**
     * The runtime the page is drawn by.
     *
     * <p>Not GeckoRuntime.getDefaultRuntime(): that method is not in this build's dex - the gate
     * named it before the shell shipped, which is what the name check is for. The engine view in
     * the tree can answer instead: GeckoView.getSession().getRuntime().</p>
     */
    private static Object runtimeOf(Activity activity) {
        try {
            java.util.ArrayDeque<View> queue = new java.util.ArrayDeque<>();
            View root = activity.findViewById(android.R.id.content);
            if (root != null) {
                queue.add(root);
            }
            for (int guard = 0; guard < 400 && !queue.isEmpty(); guard++) {
                View view = queue.removeFirst();
                if ("org.mozilla.geckoview.GeckoView".equals(view.getClass().getName())) {
                    Object session = call(view, "getSession");
                    if (session == null) {
                        continue;
                    }
                    return call(session, "getRuntime");
                }
                if (view instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) view;
                    for (int i = 0; i < group.getChildCount(); i++) {
                        queue.add(group.getChildAt(i));
                    }
                }
            }
        } catch (Throwable t) {
            SlothLog.note("library: engine view lookup failed (" + t.getClass().getSimpleName() + ")");
        }
        return null;
    }

    private static Object call(Object target, String name) throws Exception {
        java.lang.reflect.Method method = target.getClass().getMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    /** The list() a storage object offers, with whatever arity this build has. */
    private static Object listOn(Object store) throws Exception {
        java.lang.reflect.Method best = null;
        for (java.lang.reflect.Method m : store.getClass().getMethods()) {
            if (!"list".equals(m.getName())) {
                continue;
            }
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 1 && (p[0] == int.class || p[0] == long.class)) {
                best = m;
                break;
            }
            if (p.length == 0 && best == null) {
                best = m;
            }
        }
        if (best == null) {
            throw new NoSuchMethodException("no list() on " + store.getClass().getSimpleName());
        }
        best.setAccessible(true);
        if (best.getParameterTypes().length == 1) {
            Object arg = best.getParameterTypes()[0] == int.class
                    ? Integer.valueOf(200) : Long.valueOf(200L);
            return best.invoke(store, arg);
        }
        return best.invoke(store);
    }

    /** GeckoResult completes on the main thread; fill when it does, and notice when it does not. */
    private void accept(Object result) throws Exception {
        final Class<?> listener = Class.forName("org.mozilla.geckoview.GeckoResult$OnValueListener");
        Object proxy = java.lang.reflect.Proxy.newProxyInstance(listener.getClassLoader(),
                new Class<?>[]{listener}, new java.lang.reflect.InvocationHandler() {
                    @Override
                    public Object invoke(Object p, java.lang.reflect.Method m, Object[] a) {
                        if ("onValue".equals(m.getName()) && a != null && a.length >= 3) {
                            Object value = a[a.length - 1];
                            render(value);
                        }
                        return null;
                    }
                });
        result.getClass().getMethod("accept", listener).invoke(result, proxy);

        final String asked = kind;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!asked.equals(kind) || !"Asking the browser for its list\u2026"
                        .contentEquals(note.getText())) {
                    return;
                }
                unavailable("the browser's own list", new IllegalStateException("no answer"));
            }
        }, 2500);
    }

    private void render(Object value) {
        java.util.ArrayList<String[]> rows = new java.util.ArrayList<>();
        if (value instanceof java.lang.Iterable) {
            for (Object item : (java.lang.Iterable<?>) value) {
                if (item == null) {
                    continue;
                }
                String url = first(item, "getUrl", "getURL", "getUri", "getUriString");
                String name = first(item, "getTitle");
                if (url == null) {
                    continue;
                }
                rows.add(new String[]{url, name});
            }
        }
        list.removeAllViews();
        if (rows.isEmpty()) {
            note.setVisibility(View.VISIBLE);
            note.setText("bookmarks".equals(kind) ? "Nothing saved yet." : "No history yet.");
            return;
        }
        note.setVisibility(View.VISIBLE);
        note.setText(rows.size() + (rows.size() == 1 ? " page" : " pages")
                + ("bookmarks".equals(kind) ? ", newest first." : ", most recent first."));
        for (String[] row : rows) {
            list.addView(row(row[0], row[1]));
        }
    }

    private View row(String url, String name) {
        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(0, dip(activity, 11), 0, dip(activity, 11));
        cell.setClickable(true);
        cell.setFocusable(true);
        cell.setBackground(pill(activity, SlothTheme.surface(), SlothTheme.lineAt(0x14)));
        String shown = name == null || name.trim().isEmpty() ? url : name.trim();
        cell.setContentDescription(shown);

        TextView heading = label(activity, shown, 14.5f, SlothTheme.ink(), false);
        heading.setMaxLines(1);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cell.addView(heading);

        TextView under = label(activity, url, 11.5f, SlothTheme.muted(), false);
        under.setMaxLines(1);
        under.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        under.setPadding(0, dip(activity, 3), 0, 0);
        cell.addView(under);

        final String target = url;
        cell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sink.openUrl(target);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dip(activity, 6);
        cell.setLayoutParams(lp);
        return cell;
    }

    /** Ask an item for the first of several getter names that exists, as a string. */
    private static String first(Object item, String... names) {
        for (String name : names) {
            try {
                java.lang.reflect.Method m = item.getClass().getMethod(name);
                m.setAccessible(true);
                Object v = m.invoke(item);
                if (v != null && String.valueOf(v).trim().length() > 0) {
                    return String.valueOf(v);
                }
            } catch (Throwable ignored) {
                // try the next name
            }
        }
        return null;
    }

    private static TextView label(Activity activity, String text, float size, int color,
                                 boolean bold) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return view;
    }

    private static android.graphics.drawable.Drawable pill(Activity activity, int fill,
                                                          int stroke) {
        android.graphics.drawable.GradientDrawable shape =
                new android.graphics.drawable.GradientDrawable();
        shape.setCornerRadius(dip(activity, 14));
        shape.setColor(fill);
        shape.setStroke(Math.max(1, dip(activity, 1)), stroke);
        return shape;
    }

    private static int dip(Activity activity, float dp) {
        return Math.round(dp * activity.getResources().getDisplayMetrics().density);
    }
}
