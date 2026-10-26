package com.sloth.browser;

import android.app.Activity;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The settings screen, drawn in the same material as the drawer.
 *
 * <p>The browser's own panel opens onto a blank internal page on this build, so this page
 * carries the settings itself rather than pointing at it. Every switch is bound to a field
 * of the app's own settings model by name and stored through the app's own save method -
 * nothing here is a preference of ours pretending to be the browser's.</p>
 */
final class SlothSettings {

    private SlothSettings() {
    }

    /** Booleans the browser stores, in the order a person would look for them. */
    /** Fourteen switches, in groups a person can scan instead of one long stack. */
    private static final String[][][] GROUPS = {
            {  // heading, then its rows
                    {"TRACKING"},
                    {"blockTrackers", "Block trackers", "blocks known trackers"},
                    {"dnt", "Do Not Track", "asks sites not to follow you"},
                    {"gpc", "Global Privacy Control", "tells sites not to sell data"},
                    {"fingerprintResist", "Resist fingerprinting", "blurs what identifies you"},
                    {"privateDefault", "Private by default", "new tabs start private"},
                    {"screenGuard", "Block screenshots", "screenshots come out blank"},
            },
            {
                    {"CONTENT"},
                    {"javascript", "JavaScript", "off breaks most sites"},
                    {"acceptCookies", "Cookies", "keep site logins working"},
                    {"thirdPartyCookies", "Third-party cookies", "cookies from other sites"},
                    {"popups", "Allow popups", "lets a site open a window"},
                    {"blockAutoplay", "Block autoplay", "nothing starts on its own"},
                    {"prefetch", "Prefetch links", "fetches pages before you tap"},
            },
            {
                    {"CONNECTION"},
                    {"httpsOnly", "HTTPS only", "refuses an insecure page"},
                    {"webrtcBlock", "Block WebRTC leaks", "no local address through calls"},
            },
    };

    static View build(final Activity activity, final Runnable onClose, final Runnable dismiss) {
        FrameLayout page = new FrameLayout(activity);
        page.setTag("settings-page");
        page.setBackgroundColor(SlothTheme.bg());

        final LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(activity, 18), dp(activity, 14), dp(activity, 18), dp(activity, 18));

        LinearLayout head = new LinearLayout(activity);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, 0, 0, dp(activity, 14));

        TextView back = text(activity, "\u2039", 24f, SlothTheme.ink(), false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back to the drawer");
        back.setBackground(box(activity, 999, SlothTheme.card(), SlothTheme.hairline(0x24)));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClose.run();
            }
        });
        head.addView(back, lp(activity, 40, 40, 0, 0, 0, 0));

        TextView title = text(activity, "Settings", 22f, SlothTheme.ink(), true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = dp(activity, 12);
        head.addView(title, titleLp);
        column.addView(head);

        final boolean reached = SlothPrefs.has(activity);
        TextView sub = text(activity, reached
                        ? "The browser's own settings, in the drawer's clothes."
                        : "The browser's settings could not be reached - see Recent problems.",
                12f, SlothTheme.muted(), false);
        sub.setPadding(0, 0, 0, dp(activity, 10));
        column.addView(sub);

        for (String[][] group : GROUPS) {
            section(activity, column, group[0][0]);
            for (int i = 1; i < group.length; i++) {
                String[] flag = group[i];
                toggle(activity, column, flag[0], flag[1], flag[2]);
            }
        }

        section(activity, column, "READING");
        stepper(activity, column, "textZoom", "Text zoom", 75, 150, 25, "%", false);
        stepper(activity, column, "autoLockMin", "Auto-lock", 0, 15, 5, " min", true);

        section(activity, column, "LOOK AND FEEL");
        row(activity, column, "Palette", SlothTheme.name(), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClose.run();
                SlothShell.openPalettePicker(activity);
            }
        });
        row(activity, column, "Widgets on the drawer", "choose what the drawer holds",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onClose.run();
                        SlothShell.openWidgetChooser(activity);
                    }
                });

        section(activity, column, "DATA");
        action(activity, column, "Export settings", "a file you can keep", "exportSettings");
        action(activity, column, "Import settings", "restore from that file", "importSettings");
        action(activity, column, "Export bookmarks", "bookmarks and history", "exportVault");
        action(activity, column, "Import bookmarks", "bring a vault back", "importVault");
        action(activity, column, "Clear browsing data", "asks before it deletes", "clearData");

        section(activity, column, "ENGINE");
        action(activity, column, "Restart the engine", "when a page stops responding",
                "startEngine");
        action(activity, column, "Clear engine data", "site storage and caches", "clearGecko");
        action(activity, column, "Add to home screen", "put Sloth on your launcher", "addToHome");

        section(activity, column, "IF SOMETHING IS WRONG");
        row(activity, column, "Diagnostics",
                "page, frame, seats and log - copyable",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        SlothShell.openDiagPage(activity);
                    }
                });
        row(activity, column, "Recent problems",
                SlothLog.count() == 0 ? "none since the app started"
                        : SlothLog.count() + " recorded",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        new android.app.AlertDialog.Builder(activity)
                                .setTitle("Recent problems")
                                .setMessage(SlothLog.text())
                                .setPositiveButton("Clear", new android.content.DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(android.content.DialogInterface dialog, int which) {
                                        SlothLog.clear();
                                    }
                                })
                                .setNegativeButton("Close", null)
                                .show();
                    }
                });

        ScrollView scroller = new ScrollView(activity);
        scroller.setFillViewport(true);
        scroller.addView(column);
        page.addView(scroller);

        // the page runs to the bottom of the screen, so it has to leave the system bar its
        // own room - the same fault the drawer had, and the same fix
        final int base = column.getPaddingBottom();
        page.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets insets) {
                int bottom = insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom;
                if (bottom > 0) {
                    column.setPadding(column.getPaddingLeft(), column.getPaddingTop(),
                            column.getPaddingRight(), base + bottom);
                }
                return insets;
            }
        });
        column.setPadding(column.getPaddingLeft(), column.getPaddingTop(),
                column.getPaddingRight(), base + Math.max(dp(activity, 48), SlothShell.navInset(activity)));

        // Back is not answered here. A focused view does get the key first, and this page used
        // to answer it by rebuilding the whole shell - which skipped the back order the shell
        // tests actually cover (and shut the drawer on the way). SlothBack owns the key and
        // calls backOneLayer, so one press closes one layer.
        page.setFocusableInTouchMode(true);
        return page;
    }

    /**
     * What the browser itself defaults each switch to.
     *
     * <p>Reading a setting can fail, and the old fallback was `false` for every row. On the five
     * rows whose model default is `true` - JavaScript, cookies, autoplay, tracker blocking,
     * screenshot blocking - a failed read then showed the exact opposite of the shipped state, and
     * the row looked like something the person had turned off.</p>
     */
    static boolean defaultFor(String field) {
        switch (field) {
            case "screenGuard":
            case "javascript":
            case "acceptCookies":
            case "blockAutoplay":
            case "blockTrackers":
                return true;
            default:
                return false;
        }
    }

    /** The same for the two steppers, whose defaults are not their minimums. */
    static int defaultNumberFor(String field) {
        if ("textZoom".equals(field)) {
            return 100;
        }
        if ("autoLockMin".equals(field)) {
            return 5;
        }
        return 0;
    }

    /** A switch bound to one boolean of the browser's own settings. */
    private static void toggle(final Activity activity, LinearLayout column, final String field,
                               String name, String note) {
        final boolean[] current = {SlothPrefs.flag(activity, field, defaultFor(field))};
        final FrameLayout track = switchFor(activity, current[0]);

        LinearLayout row = rowWith(activity, column, name, note,
                current[0] ? name + ", on" : name + ", off", track,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean wanted = !current[0];
                        boolean ok = SlothPrefs.write(activity, field, wanted);
                        if (!ok) {
                            // the browser refused the write. A switch that stays flipped
                            // claims a setting is on when nothing was saved, so it goes
                            // back where it was and the row says why
                            SlothLog.error("could not save " + field);
                            paintSwitch(activity, track, current[0]);
                            return;
                        }
                        current[0] = wanted;
                        paintSwitch(activity, track, current[0]);
                        v.setContentDescription(
                                current[0] ? v.getContentDescription().toString()
                                        .replace(", off", ", on")
                                        : v.getContentDescription().toString()
                                        .replace(", on", ", off"));
                    }
                });
        if (row != null) {
            row.setTag("settings-row");
        }
    }

    /**
     * A switch that looks like one.
     *
     * <p>Word-only state read as a label, not a control: there was nothing on the row to
     * say "this moves". The word is still there, in the content description, where a
     * screen reader reads it and a sighted reader does not need it twice.</p>
     */
    private static FrameLayout switchFor(Activity activity, boolean on) {
        FrameLayout track = new FrameLayout(activity);
        track.setLayoutParams(new LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 26)));
        paintSwitch(activity, track, on);
        return track;
    }

    private static void paintSwitch(Activity activity, FrameLayout track, boolean on) {
        track.setTag(on ? "switch-on" : "switch-off");
        track.setBackground(box(activity, 999,
                on ? SlothTheme.accent() : SlothTheme.card(),
                on ? 0 : SlothTheme.hairline(0x55)));
        track.removeAllViews();
        View knob = new View(activity);
        knob.setBackground(box(activity, 999,
                on ? SlothTheme.accentInk() : SlothTheme.muted(), 0));
        FrameLayout.LayoutParams klp = new FrameLayout.LayoutParams(
                dp(activity, 18), dp(activity, 18));
        klp.gravity = (on ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL;
        klp.leftMargin = dp(activity, 4);
        klp.rightMargin = dp(activity, 4);
        track.addView(knob, klp);
    }

    /** A number the browser stores, stepped in place. */
    private static void stepper(final Activity activity, LinearLayout column, final String field,
                                String name, final int min, final int max, final int step,
                                final String suffix, final boolean zeroIsNever) {
        final int[] current = {SlothPrefs.number(activity, field,
                defaultNumberFor(field) != 0 ? defaultNumberFor(field) : min)};
        final TextView value = text(activity, show(current[0], suffix, zeroIsNever), 12.5f,
                SlothTheme.accent(), true);
        value.setGravity(Gravity.CENTER);
        value.setMinWidth(dp(activity, 56));

        rowWith(activity, column, name, "tap to change", name + ", " + value.getText(),
                value, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int next = current[0] + step;
                if (next > max) {
                    next = min;
                }
                current[0] = next;
                // the toggles above revert when a write is refused; a stepper that paints the new
            // number anyway is the same lie in a different control
            if (!SlothPrefs.write(activity, field, next)) {
                SlothLog.error("could not save " + field);
                value.setText(show(current[0], suffix, zeroIsNever));
                return;
            }
                value.setText(show(next, suffix, zeroIsNever));
            }
        });
    }

    private static String show(int value, String suffix, boolean zeroIsNever) {
        if (zeroIsNever && value <= 0) {
            return "never";
        }
        return value + suffix;
    }

    /** A row that calls one of the browser's private methods. */
    private static void action(final Activity activity, LinearLayout column, String name,
                               String note, final String method) {
        final String label = name;
        row(activity, column, name, note, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean ok = call(activity, method);
                android.widget.Toast.makeText(activity,
                        ok ? result(method) : label + " is not available",
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** What the action actually set in motion - "opened" was a lie on the export rows. */
    private static String result(String method) {
        if ("exportSettings".equals(method)) {
            return "Export started";
        }
        if ("importSettings".equals(method)) {
            return "Pick a settings file";
        }
        if ("exportVault".equals(method)) {
            return "Bookmarks export started";
        }
        if ("importVault".equals(method)) {
            return "Pick a vault file";
        }
        if ("clearData".equals(method)) {
            return "Choose what to clear";
        }
        if ("startEngine".equals(method)) {
            return "Engine restarting";
        }
        if ("clearGecko".equals(method)) {
            return "Clearing engine data";
        }
        if ("addToHome".equals(method)) {
            return "Add to home screen";
        }
        return method;
    }

    /**
     * Private methods are invisible to getMethod, which is exactly why the old Settings
     * tap did nothing. Walk the class chain and open them up.
     */
    static boolean call(Activity activity, String name) {
        Class<?> type = activity.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Method m = type.getDeclaredMethod(name);
                m.setAccessible(true);
                m.invoke(activity);
                return true;
            } catch (NoSuchMethodException e) {
                type = type.getSuperclass();
            } catch (Throwable t) {
                SlothLog.note(name + " threw " + t.getClass().getSimpleName());
                return false;
            }
        }
        SlothLog.note(name + " is not on " + activity.getClass().getSimpleName());
        return false;
    }

    /**
     * A section heading, with a hairline under it.
     *
     * <p>The rows are cards, and a stack of cards has no grouping of its own - the headings
     * were the same weight as the helper text under every label, so the page read as one
     * long list. Brighter, with more air above than below and a rule beneath, they group
     * without adding another box.</p>
     */
    private static void section(Activity activity, LinearLayout column, String name) {
        TextView t = text(activity, name, 11f, SlothTheme.dim(), true);
        t.setLetterSpacing(0.16f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(activity, 18);
        lp.bottomMargin = dp(activity, 6);
        column.addView(t, lp);

        View rule = new View(activity);
        rule.setBackgroundColor(SlothTheme.hairline(0x2A));
        LinearLayout.LayoutParams ruleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(activity, 1)));
        ruleLp.bottomMargin = dp(activity, 8);
        column.addView(rule, ruleLp);
    }

    private static void row(Activity activity, LinearLayout column, String name, String value,
                            View.OnClickListener onClick) {
        rowWith(activity, column, name, value, null, null, onClick);
    }

    private static LinearLayout rowWith(Activity activity, LinearLayout column, String name,
                                        String value, String spoken, View trailing,
                                        View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 14), 0, dp(activity, 12), 0);
        row.setClickable(true);
        row.setFocusable(true);
        row.setTag("settings-row");
        row.setContentDescription(spoken == null ? name : spoken);
        row.setBackground(box(activity, Math.max(10, Math.round(SlothTheme.radius()) - 4),
                SlothTheme.card(), SlothTheme.hairline(0x1A)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 54));
        lp.bottomMargin = dp(activity, 8);
        row.setLayoutParams(lp);

        LinearLayout words = new LinearLayout(activity);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setGravity(Gravity.CENTER_VERTICAL);
        words.addView(text(activity, name, 14.5f, SlothTheme.ink(), false));
        if (value != null && value.length() > 0) {
            TextView v = text(activity, value, 11.5f, SlothTheme.muted(), false);
            v.setPadding(0, dp(activity, 2), 0, 0);
            words.addView(v);
        }
        row.addView(words, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (trailing != null) {
            row.addView(trailing);
        } else {
            TextView chevron = text(activity, "\u203a", 18f, SlothTheme.muted(), false);
            chevron.setGravity(Gravity.CENTER);
            row.addView(chevron, lp(activity, 24, 24, 0, 0, 0, 0));
        }

        row.setOnClickListener(onClick);
        column.addView(row);
        return row;
    }

    private static TextView text(Activity activity, String value, float size, int colour,
                                 boolean bold) {
        TextView t = new TextView(activity);
        t.setText(value);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTextColor(colour);
        if (bold) {
            t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return t;
    }

    private static android.graphics.drawable.GradientDrawable box(Activity activity, int radiusDp,
                                                                  int fill, int stroke) {
        android.graphics.drawable.GradientDrawable d =
                new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        d.setCornerRadius(radiusDp >= 999 ? dp(activity, 999) : dp(activity, radiusDp));
        d.setColor(fill);
        if (stroke != 0) {
            d.setStroke(Math.max(1, dp(activity, 1)), stroke);
        }
        return d;
    }

    private static LinearLayout.LayoutParams lp(Activity activity, int w, int h, int l, int t,
                                                int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(activity, w), dp(activity, h));
        p.leftMargin = dp(activity, l);
        p.topMargin = dp(activity, t);
        p.rightMargin = dp(activity, r);
        p.bottomMargin = dp(activity, b);
        return p;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
