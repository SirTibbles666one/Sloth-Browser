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
        row.addView(buildField(), new LinearLayout.LayoutParams(0, dp(44), 1f));
        countButton = buildCount();
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(dp(44), dp(44));
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
        row.addView(menu, new LinearLayout.LayoutParams(dp(44), dp(44)));
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
        pill.addView(siteIcon, new LinearLayout.LayoutParams(dp(36), dp(40)));

        field = new EditText(activity);
        field.setTag("address");
        field.setSingleLine(true);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
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
        pill.addView(clear, new LinearLayout.LayoutParams(dp(40), dp(40)));

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
        pill.addView(reload, new LinearLayout.LayoutParams(dp(40), dp(40)));

        field.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                CharSequence typed = field.getText();
                if (typed == null || typed.toString().trim().length() == 0) {
                    return false;
                }
                field.clearFocus();
                hideKeyboard();
                host.go(field);
                return true;
            }
        });
        field.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
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
                describeSite(s == null ? "" : s.toString());
            }

            public void afterTextChanged(android.text.Editable s) {
            }
        });
        field.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return copy(field.getText());
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
        boolean secure = secure(text);
        siteIcon.tint(secure ? SlothTheme.dim() : 0xFFE2A05A);
        siteIcon.setContentDescription(secure
                ? "Site settings, secure connection"
                : "Site settings, not a secure address");
    }

    static boolean secure(String url) {
        String u = url == null ? "" : url.trim().toLowerCase(java.util.Locale.UK);
        return u.startsWith("https://") || u.startsWith("about:") || u.length() == 0;
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
        countFace.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
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

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
