package com.sloth.browser;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The top bar: a search field first, then the tabs and the menu.
 *
 * <p>Three controls, not seven. The field is the widest thing on the screen because it is the
 * thing you use most, and reload lives inside it, at the end, where a browser's address field
 * usually keeps it. Back and forward are in the drawer and on a sideways swipe across the
 * field; the tab count opens the strip of tabs and the menu opens the whole drawer.</p>
 *
 * <p>The bar only builds views and reports what was asked. Everything that touches the browser
 * goes through {@link Host}, so the shell stays the one place that knows the app.</p>
 */
final class SlothTopBar {

    /** What the bar needs from the shell. */
    interface Host {
        /** Navigate to, or search for, what the field holds. */
        void go(EditText field);

        /** The tab count was tapped: show the tabs. */
        void showTabs();

        /** The tab count was held: the browser's own switcher, if it has one. */
        boolean showSwitcher();

        /** The menu was tapped: the whole drawer. */
        void showMenu();

        void reload();

        /** Reload was held, or tapped while the page loads: stop loading. */
        boolean stop();

        /** The padlock was tapped: the site's own information. */
        void siteInfo();

        /** A sideways swipe along the field; +1 is the next tab. */
        void switchTab(int direction);
    }

    /** Sideways travel that counts as a tab swipe on the field. */
    static final int SWIPE_DP = 56;

    private final Activity activity;
    private final Host host;
    private final LinearLayout row;
    private LinearLayout pill;
    private GradientDrawable pillBg;
    private SlothIcons siteIcon;
    private EditText field;
    private View clear;
    private View reload;
    private SlothIcons reloadIcon;
    private View progressLine;
    private boolean loading;
    /** The page's real address. The field shows only its site until you tap in to edit it. */
    private String raw = "";
    private TextView countFace;
    private View countButton;
    private View menu;

    SlothTopBar(Activity activity, Host host) {
        this.activity = activity;
        this.host = host;
        row = new LinearLayout(activity);
        row.setTag("bar");
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), 0, dp(4), 0);
        // the surface owns the only fill; a bar with its own would be a box inside a box
        row.setBackground(null);
        row.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56), Gravity.TOP));
        row.addView(buildField(), new LinearLayout.LayoutParams(0, dp(48), 1f));
        countButton = buildCount();
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        countLp.leftMargin = dp(4);
        row.addView(countButton, countLp);
        menu = iconButton(SlothIcons.Kind.MENU, "Menu", 22);
        menu.setTag("bar-menu");
        menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.showMenu();
            }
        });
        row.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
    }

    View view() {
        return row;
    }

    EditText field() {
        return field;
    }

    View pill() {
        return pill;
    }

    /**
     * The page's address, from the browser. While the field is idle it shows the site alone -
     * {@code example.org}, not {@code https://www.example.org/a/b?c} - and the whole address comes
     * back the moment you tap in. A field being typed in is never written over.
     */
    void showAddress(String url) {
        raw = url == null ? "" : url.trim();
        if (!field.hasFocus()) {
            String shown = display(raw);
            if (!shown.contentEquals(field.getText())) {
                field.setText(shown);
            }
            describeSite(raw);
        }
    }

    /** The page's address as the browser last reported it, whatever is being typed. */
    String pageAddress() {
        return raw;
    }

    /** The real address, or what is being typed: never the shortened form. */
    String address() {
        return field.hasFocus() ? String.valueOf(field.getText()) : raw;
    }

    /**
     * The site part of an address, the way phone browsers show it at rest: no scheme, no
     * "www.", no path. Anything that is not an address - a search phrase, about:blank - is
     * shown as it is. Whether the connection is secure is said by the padlock, not the text.
     */
    static String display(String url) {
        String value = url == null ? "" : url.trim();
        String lower = value.toLowerCase(java.util.Locale.UK);
        if (!(lower.startsWith("http://") || lower.startsWith("https://"))
                || !SlothShell.looksLikeAddress(value)) {
            return value;
        }
        String rest = value.substring(value.indexOf("://") + 3);
        int end = rest.length();
        for (char stop : new char[]{'/', '?', '#'}) {
            int at = rest.indexOf(stop);
            if (at >= 0 && at < end) {
                end = at;
            }
        }
        String host = rest.substring(0, end);
        int user = host.lastIndexOf('@');
        if (user >= 0) {
            host = host.substring(user + 1);
        }
        if (host.toLowerCase(java.util.Locale.UK).startsWith("www.") && host.length() > 4) {
            host = host.substring(4);
        }
        return host.isEmpty() ? value : host;
    }

    View countButton() {
        return countButton;
    }

    View menuButton() {
        return menu;
    }

    View reloadButton() {
        return reload;
    }

    SlothIcons siteIcon() {
        return siteIcon;
    }

    boolean loading() {
        return loading;
    }

    /** A load began or ended: reload becomes Stop while it runs, and the line shows. */
    void setLoading(boolean now) {
        if (loading == now) {
            return;
        }
        loading = now;
        reloadIcon.kind(now ? SlothIcons.Kind.CLOSE : SlothIcons.Kind.RELOAD);
        reload.setContentDescription(now ? "Stop loading" : "Reload");
        if (now) {
            progressLine.animate().cancel();
            progressLine.setAlpha(1f);
            progressLine.setScaleX(0.05f);
            progressLine.setVisibility(View.VISIBLE);
        } else {
            // finish the line, then let it go: a load that ends at 60% still ended
            progressLine.setScaleX(1f);
            progressLine.setVisibility(View.GONE);
        }
    }

    /**
     * Show a load exactly as it stands: used when the session on screen changes, since the
     * other tab may be less far along than the line was.
     */
    void showLoad(boolean now, int percent) {
        setLoading(now);
        if (now) {
            progressLine.setScaleX(Math.max(0.05f, Math.min(1f, percent / 100f)));
        }
    }

    /** How far the load has got, 0-100. Never moves backwards within one load. */
    void setProgress(int percent) {
        if (!loading) {
            return;
        }
        float fraction = Math.max(0.05f, Math.min(1f, percent / 100f));
        if (fraction > progressLine.getScaleX()) {
            progressLine.setScaleX(fraction);
        }
    }

    /** Every control, for the shell's fade and its accessibility checks. */
    View[] controls() {
        return new View[]{pill, countButton, menu};
    }

    /** The count on the tab button, said in words for TalkBack. */
    void setTabCount(int count) {
        // two digits fit the square; past that the number stops being information
        String shown = count > 99 ? "99+" : String.valueOf(Math.max(0, count));
        if (!shown.contentEquals(countFace.getText())) {
            countFace.setText(shown);
        }
        countButton.setContentDescription((count == 1 ? "1 tab" : count + " tabs")
                + ". Show the tabs");
    }

    /* ------------------------------------------------------------------- pill */
    /**
     * The field and, along its bottom edge, the page's loading progress. The line is a scale,
     * not a width: a load reports many times a second, and a layout per report would be paid by
     * the page underneath.
     */
    private View buildField() {
        FrameLayout frame = new FrameLayout(activity);
        frame.setTag("address-pill");
        frame.addView(buildPill(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        progressLine = new View(activity);
        progressLine.setTag("progress-line");
        GradientDrawable line = new GradientDrawable();
        line.setCornerRadius(dp(2));
        line.setColor(SlothTheme.accent());
        progressLine.setBackground(line);
        progressLine.setPivotX(0f);
        progressLine.setScaleX(0f);
        progressLine.setVisibility(View.GONE);
        progressLine.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams lineLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.BOTTOM);
        lineLp.leftMargin = dp(18);
        lineLp.rightMargin = dp(18);
        lineLp.bottomMargin = dp(1);
        frame.addView(progressLine, lineLp);
        return frame;
    }

    private View buildPill() {
        pill = new LinearLayout(activity);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(4), 0, dp(2), 0);
        pillBg = new GradientDrawable();
        pillBg.setCornerRadius(dp(22));
        pillBg.setColor(SlothTheme.field());
        pillBg.setStroke(Math.max(1, dp(1)), SlothTheme.lineAt(0x2E));
        pill.setBackground(pillBg);

        siteIcon = SlothIcons.of(activity, SlothIcons.Kind.SEARCH, 18, SlothTheme.muted());
        siteIcon.setTag("site-icon");
        siteIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.siteInfo();
            }
        });
        // a 48 dp box for the finger, the mark drawn at the 36 dp it always was
        siteIcon.setPadding(dp(6), dp(6), dp(6), dp(6));
        pill.addView(siteIcon, new LinearLayout.LayoutParams(dp(48), dp(48)));

        field = new EditText(activity);
        field.setTag("address");
        field.setSingleLine(true);
        // the bar is a fixed height: its text follows the phone's text size up to 1.3x, and
        // stops there so the address is not cut to a few letters
        field.setTextSize(TypedValue.COMPLEX_UNIT_DIP, cappedSp(15f));
        field.setTextColor(SlothTheme.ink());
        field.setHintTextColor(SlothTheme.dim());
        field.setHint("Search or open");
        field.setContentDescription("Search or open");
        field.setBackground(null);
        field.setPadding(dp(2), 0, dp(4), 0);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        field.setImeOptions(EditorInfo.IME_ACTION_GO);
        field.setSelectAllOnFocus(true);
        pill.addView(field, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        clear = iconButton(SlothIcons.Kind.CLOSE, "Clear the field", 14);
        clear.setTag("bar-clear");
        clear.setVisibility(View.GONE);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                field.setText("");
                field.requestFocus();
            }
        });
        pill.addView(clear, new LinearLayout.LayoutParams(dp(48), dp(48)));

        reload = iconButton(SlothIcons.Kind.RELOAD, "Reload", 18);
        reload.setTag("bar-reload");
        reloadIcon = (SlothIcons) ((ViewGroup) reload).getChildAt(0);
        reload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // while the page loads this is Stop: the control at the end of a loading field
                // is the one people reach for when a page hangs
                if (loading) {
                    host.stop();
                } else {
                    host.reload();
                }
            }
        });
        reload.setLongClickable(true);
        reload.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return host.stop();
            }
        });
        pill.addView(reload, new LinearLayout.LayoutParams(dp(48), dp(48)));

        field.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                CharSequence typed = field.getText();
                if (typed == null || typed.toString().trim().length() == 0) {
                    return false;
                }
                // sent first, while the field still holds every character typed: letting go of
                // focus puts the short form back in the field
                raw = typed.toString().trim();
                host.go(field);
                field.clearFocus();
                hideKeyboard();
                return true;
            }
        });
        field.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                // tap in: the whole address, selected, ready to replace; leave: the site again
                String wanted = hasFocus ? raw : display(raw);
                if (!wanted.contentEquals(field.getText())) {
                    field.setText(wanted);
                }
                if (hasFocus) {
                    field.selectAll();
                }
                pillBg.setStroke(Math.max(1, dp(hasFocus ? 2 : 1)),
                        hasFocus ? SlothTheme.accent() : SlothTheme.lineAt(0x2E));
                showEditing(hasFocus);
            }
        });
        field.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
                showEditing(field.hasFocus());
                describeSite(field.hasFocus() ? (s == null ? "" : s.toString()) : raw);
            }

            public void afterTextChanged(android.text.Editable s) {
            }
        });
        field.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return copy(address());
            }
        });
        // a sideways swipe along the field walks the tabs; until the field has focus it is a
        // label, and a label can be swiped
        pill.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private boolean swiped;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getX();
                        downY = e.getY();
                        swiped = false;
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        if (!field.hasFocus() && !swiped) {
                            float dx = e.getX() - downX;
                            float dy = e.getY() - downY;
                            if (Math.abs(dx) > dp(SWIPE_DP) && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                                swiped = true;
                                host.switchTab(dx < 0 ? 1 : -1);
                                return true;
                            }
                        }
                        return false;
                    default:
                        return false;
                }
            }
        });
        describeSite("");
        return pill;
    }

    /** While typing, the clear control replaces reload: one control at the end, never two. */
    private void showEditing(boolean editing) {
        boolean typed = field.getText() != null && field.getText().length() > 0;
        clear.setVisibility(editing && typed ? View.VISIBLE : View.GONE);
        reload.setVisibility(editing ? View.GONE : View.VISIBLE);
    }

    /**
     * The icon at the start of the field. A search phrase gets a magnifier - a padlock there
     * would claim a security state for a page that does not exist yet. An address gets the
     * padlock, ink when the connection is encrypted and amber when it is not, and the words
     * say which.
     */
    private void describeSite(String text) {
        boolean address = SlothShell.looksLikeAddress(text);
        siteIcon.kind(address ? SlothIcons.Kind.LOCK : SlothIcons.Kind.SEARCH);
        siteIcon.setClickable(address);
        siteIcon.setFocusable(address);
        if (!address) {
            siteIcon.tint(SlothTheme.muted());
            siteIcon.setContentDescription("Search");
            return;
        }
        boolean secure = secure(text, field != null && field.hasFocus());
        siteIcon.tint(secure ? SlothTheme.dim() : 0xFFE2A05A);
        siteIcon.setContentDescription(secure
                ? "Site settings, secure connection"
                : "Site settings, not a secure address");
    }

    /** {@link #secure(String, boolean)} for a page that is showing. */
    static boolean secure(String url) {
        return secure(url, false);
    }

    /**
     * Whether the padlock says the connection is secure. {@code https} and {@code about:}
     * are; an explicit {@code http} address is not. The browser's own field drops the
     * scheme once a page is showing, so a host on its own is that display, not a typed
     * insecure address - while it is being typed there is no page yet to vouch for.
     */
    static boolean secure(String url, boolean editing) {
        String u = url == null ? "" : url.trim().toLowerCase(java.util.Locale.UK);
        if (u.length() == 0 || u.startsWith("https://") || u.startsWith("about:")) {
            return true;
        }
        if (u.startsWith("http://")) {
            return false;
        }
        return !editing && SlothShell.looksLikeAddress(u);
    }

    private boolean copy(CharSequence value) {
        if (value == null || value.length() == 0) {
            return false;
        }
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                    activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) {
                return false;
            }
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Address", value));
            Toast.makeText(activity, "Address copied", Toast.LENGTH_SHORT).show();
            return true;
        } catch (Throwable t) {
            SlothLog.warn("could not copy the address: " + t.getClass().getSimpleName());
            return false;
        }
    }

    private void hideKeyboard() {
        try {
            Object ime = activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (ime instanceof InputMethodManager) {
                ((InputMethodManager) ime).hideSoftInputFromWindow(field.getWindowToken(), 0);
            }
        } catch (Throwable ignored) {
            SlothLog.swallow("SlothTopBar.hideKeyboard", ignored);
            // the keyboard stays up; the page still loads
        }
    }

    /* ------------------------------------------------------------------ count */
    /**
     * The tab count: the number inside a drawn rounded square, the shape every phone browser
     * uses for "your tabs", so it reads without a word beside it.
     */
    private View buildCount() {
        FrameLayout button = new FrameLayout(activity);
        button.setTag("tab-count");
        button.setClickable(true);
        button.setFocusable(true);
        button.setBackground(press());
        FrameLayout box = new FrameLayout(activity);
        GradientDrawable frame = new GradientDrawable();
        frame.setCornerRadius(dp(6));
        frame.setStroke(dp(2), SlothTheme.ink());
        box.setBackground(frame);
        countFace = new TextView(activity);
        // the number sits in a drawn square of fixed size; the count is spoken in full anyway
        countFace.setTextSize(TypedValue.COMPLEX_UNIT_DIP, cappedSp(12f));
        countFace.setTextColor(SlothTheme.ink());
        countFace.setTypeface(countFace.getTypeface(), android.graphics.Typeface.BOLD);
        countFace.setIncludeFontPadding(false);
        countFace.setGravity(Gravity.CENTER);
        countFace.setText("0");
        countFace.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        box.addView(countFace, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams boxLp = new FrameLayout.LayoutParams(dp(22), dp(22));
        boxLp.gravity = Gravity.CENTER;
        button.addView(box, boxLp);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.showTabs();
            }
        });
        button.setLongClickable(true);
        button.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return host.showSwitcher();
            }
        });
        countButton = button;
        setTabCount(0);
        return button;
    }

    /* ------------------------------------------------------------------ utils */
    /** A 44 dp round control around a drawn icon. The icon is decoration; the button speaks. */
    private View iconButton(SlothIcons.Kind kind, String label, int iconDp) {
        FrameLayout button = new FrameLayout(activity);
        button.setContentDescription(label);
        button.setClickable(true);
        button.setFocusable(true);
        button.setBackground(press());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(iconDp), dp(iconDp));
        lp.gravity = Gravity.CENTER;
        button.addView(SlothIcons.decorative(activity, kind, iconDp, SlothTheme.ink()), lp);
        return button;
    }

    private android.graphics.drawable.Drawable press() {
        android.graphics.drawable.StateListDrawable states =
                new android.graphics.drawable.StateListDrawable();
        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.OVAL);
        pressed.setColor(SlothTheme.lineAt(0x22));
        GradientDrawable idle = new GradientDrawable();
        idle.setShape(GradientDrawable.OVAL);
        idle.setColor(0);
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{}, idle);
        return states;
    }

    /** A size in sp, scaled with the phone's text size but no further than 1.3x, in dp. */
    private float cappedSp(float sp) {
        float scale = activity.getResources().getConfiguration().fontScale;
        return sp * Math.min(1.3f, Math.max(0.85f, scale));
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
