package com.sloth.browser;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Find in page.
 *
 * <p>A search field that asks the engine's own finder, so matches are highlighted the way the page
 * itself highlights them rather than by anything drawn over it. The count comes from the finder's
 * result: {@code total} matches, {@code current} highlighted.</p>
 *
 * <p>Stepping with next/previous is deliberately absent: this build exposes one
 * {@code find(String, int)} and no readable flag for "the next match", and inventing a bit for it
 * would be the silent-lookup bug this project keeps removing. Search, count and clear are what can
 * be done honestly here.</p>
 */
final class SlothFind {

    /** The bar, carrying the object that answers for it. */
    static final class Bar extends FrameLayout {
        SlothFind find;

        Bar(Activity activity) {
            super(activity);
        }
    }

    private final Activity activity;
    private final TextView count;
    private final EditText field;

    private SlothFind(Activity activity, TextView count, EditText field) {
        this.activity = activity;
        this.count = count;
        this.field = field;
    }

    /** The bar, hidden, with its field bound to the engine's finder. */
    static View build(final Activity activity, final Runnable onClose) {
        Bar bar = new Bar(activity);
        bar.setTag("find-bar");
        bar.setBackgroundColor(SlothTheme.surface());

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(pixels(activity, 12), pixels(activity, 8),
                pixels(activity, 8), pixels(activity, 8));

        final EditText field = new EditText(activity);
        field.setSingleLine(true);
        field.setHint("Find in page");
        field.setTextSize(15f);
        field.setTextColor(SlothTheme.ink());
        field.setHintTextColor(SlothTheme.muted());
        field.setBackground(null);
        field.setContentDescription("Find in page");
        row.addView(field, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView count = new TextView(activity);
        count.setTextSize(12f);
        count.setTextColor(SlothTheme.muted());
        count.setPadding(pixels(activity, 8), 0, pixels(activity, 8), 0);
        count.setTag("find-count");
        row.addView(count, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView close = new TextView(activity);
        close.setText("\u00d7");
        close.setTextSize(20f);
        close.setTextColor(SlothTheme.ink());
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close find");
        close.setPadding(pixels(activity, 10), pixels(activity, 4),
                pixels(activity, 10), pixels(activity, 4));
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClose.run();
            }
        });
        row.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bar.addView(row);
        final SlothFind find = new SlothFind(activity, count, field);
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count2, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count2) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                find.search(s == null ? "" : s.toString());
            }
        });
        bar.find = find;
        return bar;
    }

    /** Ask the engine for this query and report what it found. */
    private void search(String query) {
        String text = query.trim();
        if (text.length() == 0) {
            count.setText("");
            clear(activity);
            return;
        }
        try {
            Object finder = finder();
            Object result = finder.getClass()
                    .getMethod("find", String.class, int.class)
                    .invoke(finder, text, 0);
            accept(result);
        } catch (Throwable t) {
            SlothLog.note("find unavailable: " + t.getClass().getSimpleName());
            count.setText("unavailable");
        }
    }

    /** Put every highlight away. Called on an empty field and when the bar closes. */
    static void clear(Activity activity) {
        try {
            Object session = session(activity);
            call(call(session, "getFinder"), "clear");
        } catch (Throwable t) {
            // a finder that is not there has nothing to clear
        }
    }

    /** The finder's answer: total matches and which one is highlighted. */
    private void accept(Object result) throws Exception {
        final Class<?> listener = Class.forName("org.mozilla.geckoview.GeckoResult$OnValueListener");
        Object proxy = java.lang.reflect.Proxy.newProxyInstance(listener.getClassLoader(),
                new Class<?>[]{listener}, new java.lang.reflect.InvocationHandler() {
                    @Override
                    public Object invoke(Object p, java.lang.reflect.Method m, Object[] a) {
                        if ("onValue".equals(m.getName()) && a != null && a.length >= 3) {
                            report(a[a.length - 1]);
                        }
                        return null;
                    }
                });
        result.getClass().getMethod("accept", listener).invoke(result, proxy);
    }

    private void report(Object found) {
        try {
            int total = field(found, "total");
            int current = field(found, "current");
            if (total <= 0) {
                count.setText("no matches");
            } else {
                count.setText((current <= 0 ? 1 : current) + " of " + total);
            }
        } catch (Throwable t) {
            count.setText("");
        }
    }

    private static int field(Object holder, String name) throws Exception {
        java.lang.reflect.Field f = holder.getClass().getField(name);
        Object value = f.get(holder);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private Object finder() throws Exception {
        return call(session(activity), "getFinder");
    }

    /** The engine view in this window answers for the page: GeckoView.getSession(). */
    static Object session(Activity activity) throws Exception {
        java.util.ArrayDeque<View> queue = new java.util.ArrayDeque<>();
        View root = activity.findViewById(android.R.id.content);
        if (root != null) {
            queue.add(root);
        }
        for (int guard = 0; guard < 400 && !queue.isEmpty(); guard++) {
            View view = queue.removeFirst();
            if ("org.mozilla.geckoview.GeckoView".equals(view.getClass().getName())) {
                Object session = call(view, "getSession");
                if (session != null) {
                    return session;
                }
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    queue.add(group.getChildAt(i));
                }
            }
        }
        throw new IllegalStateException("no engine view in this window");
    }

    private static Object call(Object target, String name) throws Exception {
        java.lang.reflect.Method m = target.getClass().getMethod(name);
        m.setAccessible(true);
        return m.invoke(target);
    }

    private static int pixels(Activity activity, int dp) {
        return Math.round(dp * activity.getResources().getDisplayMetrics().density);
    }
}
