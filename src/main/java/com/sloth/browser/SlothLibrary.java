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

    /**
     * The page carries its own instance.
     *
     * <p>A single static held the last page built, so a second window filled the first window's
     * page and brought an empty one to the front. A static map keyed by the page fixed that and
     * leaked instead: the value holds the page's own children, so the key could never be
     * collected and every rebuild kept a whole window alive. The page is the only thing that
     * needs to know its library, so it holds it.</p>
     */
    static final class Page extends FrameLayout {
        SlothLibrary library;

        Page(Activity activity) {
            super(activity);
        }
    }

    private final Activity activity;
    private final Sink sink;
    private final TextView title;
    private final TextView note;
    private final LinearLayout list;
    private final TextView save;
    private String kind = "bookmarks";

    private SlothLibrary(Activity activity, Sink sink, TextView title, TextView note,
                         LinearLayout list, TextView save) {
        this.activity = activity;
        this.sink = sink;
        this.title = title;
        this.note = note;
        this.list = list;
        this.save = save;
    }

    static View build(final Activity activity, final Sink sink, final Runnable onClose) {
        Page page = new Page(activity);
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

        final TextView save = label(activity, "Save page", 12f, SlothTheme.accent(), true);
        save.setTag("library-save");
        save.setPadding(dip(activity, 12), dip(activity, 8), dip(activity, 12), dip(activity, 8));
        save.setBackground(pill(activity, SlothTheme.card(), SlothTheme.lineAt(0x2E)));
        save.setVisibility(View.GONE);
        save.setContentDescription("Save this page as a bookmark");
        final SlothLibrary[] holder = new SlothLibrary[1];
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (holder[0] != null) {
                    holder[0].saveCurrent();
                }
            }
        });
        head.addView(save, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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
        holder[0] = new SlothLibrary(activity, sink, title, note, list, save);
        page.library = holder[0];
        return page;
    }

    /** Fill one page for one kind and say whether that page can be shown at all. */
    static boolean show(View page, String kind) {
        if (!(page instanceof Page) || ((Page) page).library == null) {
            return false;
        }
        ((Page) page).library.fill(kind);
        return true;
    }

    private void fill(String kind) {
        this.kind = kind;
        save.setVisibility("bookmarks".equals(kind) ? View.VISIBLE : View.GONE);
        title.setText("bookmarks".equals(kind) ? "Bookmarks"
                : "downloads".equals(kind) ? "Downloads" : "History");
        list.removeAllViews();
        note.setText("Asking the browser for its list\u2026");
        note.setVisibility(View.VISIBLE);
        if (!listing()) {
            return;
        }
    }

    /**
     * Fill from the best source this build has: the app's own vault first, the engine second.
     *
     * <p>The vault is what the browser's panels read - SlothApp holds the one instance and its
     * records are already in memory - so it is both the honest source and the cheap one. The
     * engine's storage controller is the fallback for a build whose vault is empty.</p>
     */
    private boolean listing() {
        Object list = null;
        Throwable vaultWhy = null;
        try {
            list = vaultList(kind);
        } catch (Throwable t) {
            vaultWhy = t;
        }
        if (list instanceof java.lang.Iterable && ((java.lang.Iterable<?>) list).iterator().hasNext()) {
            render(list);
            return true;
        }
        try {
            Object runtime = runtimeOf(activity);
            if (runtime == null) {
                throw new IllegalStateException("no engine view in this window");
            }
            Object storage = call(runtime, "getStorageController");
            Object store = call(storage, "bookmarks".equals(kind) ? "getBookmarks" : "getHistory");
            accept(listOn(store));
            return true;
        } catch (Throwable engineWhy) {
            if (list instanceof java.lang.Iterable) {
                // the vault answered and it is simply empty: that is an answer, not a failure
                render(list);
                return true;
            }
            unavailable("the browser's own list",
                    vaultWhy != null ? vaultWhy : engineWhy);
            return false;
        }
    }

    /**
     * The records SlothApp already has in memory.
     *
     * <p>Each name is a literal, straight through the reflective helper, so the build's name
     * gate can see them: a computed getter name is a call the gate cannot check, which is how
     * the first version of this class shipped a method that does not exist.</p>
     */
    private Object vaultList(String kind) throws Exception {
        Object vault = call(activity.getApplication(), "getVault");
        if (vault == null) {
            throw new IllegalStateException("the app has no vault");
        }
        if ("bookmarks".equals(kind)) {
            return call(vault, "getBookmarks");
        }
        if ("downloads".equals(kind)) {
            return call(vault, "getDownloads");
        }
        return call(vault, "getHistory");
    }

    /**
     * Save the page the browser is showing as a bookmark.
     *
     * <p>The vault is the app's own store, so the bookmark lands where the browser's own list
     * would put it: construct Bookmark(url, title), add it to the live list getBookmarks()
     * returns, and persist. If any of that does not resolve, say so rather than appearing to
     * save and losing it.</p>
     */
    private void saveCurrent() {
        String url = SlothShell.currentUrl(activity);
        if (url == null || url.trim().length() == 0) {
            note.setVisibility(View.VISIBLE);
            note.setText("Nothing to save: no page is open yet.");
            return;
        }
        try {
            Object vault = call(activity.getApplication(), "getVault");
            Class<?> bookmark = Class.forName("com.sloth.browser.vault.Bookmark");
            Object entry = bookmark.getConstructor(String.class, String.class)
                    .newInstance(url, titleFor(url));
            Object list = call(vault, "getBookmarks");
            if (!(list instanceof java.util.List)) {
                throw new IllegalStateException("the vault has no bookmark list");
            }
            @SuppressWarnings("unchecked")
            java.util.List<Object> bookmarks = (java.util.List<Object>) list;
            for (Object existing : bookmarks) {
                String have = first(existing, "getUrl");
                if (have != null && have.equals(url)) {
                    note.setVisibility(View.VISIBLE);
                    note.setText("Already saved.");
                    return;
                }
            }
            bookmarks.add(entry);
            call(vault, "persist");
            SlothLog.note("library saved " + url);
            fill("bookmarks");
        } catch (Throwable t) {
            SlothLog.note("library could not save: " + t.getClass().getSimpleName());
            note.setVisibility(View.VISIBLE);
            note.setText("Could not save this page. Diagnostics has the reason.");
        }
    }

    /**
     * A name for a saved page: the app's title if it exposes one, else the host.
     *
     * <p>Falling back to the whole URL put "https://example.com/a/very/long/path?with=query" in a
     * bookmark title; the host is what a person recognises in a list.</p>
     */
    private String titleFor(String url) {
        String shown = SlothShell.currentTitle(activity);
        if (shown != null && shown.trim().length() > 0) {
            return shown.trim();
        }
        try {
            String host = android.net.Uri.parse(url).getHost();
            if (host != null && host.length() > 0) {
                return host.startsWith("www.") ? host.substring(4) : host;
            }
        } catch (Throwable ignored) {
            // a URL this build cannot parse is still saved under itself
        }
        return url;
    }

    /** "3 hours ago" for a timestamp in milliseconds; empty for a timestamp that is not one. */
    static String ago(long at) {
        if (at <= 0) {
            return "";
        }
        long seconds = Math.max(0, (System.currentTimeMillis() - at) / 1000L);
        if (seconds < 60) {
            return "just now";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        }
        long days = hours / 24;
        if (days < 30) {
            return days + (days == 1 ? " day ago" : " days ago");
        }
        long months = days / 30;
        return months < 12 ? months + (months == 1 ? " month ago" : " months ago")
                : (months / 12) + ((months / 12) == 1 ? " year ago" : " years ago");
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
                // history and downloads carry when it happened; a bookmark may not, and an
                // empty stamp is left out rather than printed as 1970
                rows.add(new String[]{url, name, ago(longAt(item))});
            }
        }
        list.removeAllViews();
        if (rows.isEmpty()) {
            note.setVisibility(View.VISIBLE);
            note.setText("bookmarks".equals(kind) ? "Nothing saved yet."
                    : "downloads".equals(kind) ? "Nothing downloaded yet." : "No history yet.");
            return;
        }
        note.setVisibility(View.VISIBLE);
        note.setText(rows.size() + (rows.size() == 1 ? " page" : " pages")
                + ("bookmarks".equals(kind) ? ", newest first."
                : "downloads".equals(kind) ? ", newest first." : ", most recent first."));
        for (String[] row : rows) {
            list.addView(row(row[0], row[1], row[2]));
        }
    }

    private View row(String url, String name, String when) {
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

        TextView under = label(activity, when.length() == 0 ? url : url + "  \u00b7  " + when,
                11.5f, SlothTheme.muted(), false);
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

    /** A long getter, or 0 when the entry does not carry one. */
    private static long longAt(Object item) {
        try {
            java.lang.reflect.Method m = item.getClass().getMethod("getAt");
            m.setAccessible(true);
            Object v = m.invoke(item);
            return v instanceof Number ? ((Number) v).longValue() : 0L;
        } catch (Throwable ignored) {
            return 0L;
        }
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
