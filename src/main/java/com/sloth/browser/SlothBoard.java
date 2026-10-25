package com.sloth.browser;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The reworked dashboard, built in code.
 *
 * It replaces DrawerFace's shelf, which had three problems the audit found: colours
 * hardcoded at the call site so skins could not reach them, no contentDescription on
 * any control, and raw px text sizes that ignore the system font scale. Everything
 * here comes from one token set, every control is labelled for TalkBack, and text
 * sizes are sp so the phone's own setting applies.
 *
 * Controls forward to the browser's bound widgets with performClick(), so tabs, the
 * menu, add-ons and the updater keep their real behaviour.
 */
final class SlothBoard {

    /* tokens - a skin changes these and the whole surface follows */
    // filled from the active theme when the board is built
    static int BG = 0xFF0D1411;
    static int CARD = 0xFF1B2722;
    static int INK = 0xFFEEF7F1;
    static int DIM = 0xFFB9C9BD;
    static int ACCENT = SlothTheme.accent();
    static int ACCENT_INK = 0xFF101A12;

    private final Activity activity;
    private final LinearLayout root;

    /** How many children the dashboard has; the reveal has to reach all of them. */
    int cardCount() {
        return root == null ? 0 : root.getChildCount();
    }
    private LinearLayout tabRow;
    private TextView clock;
    private TextView editButton;
    private TextView subTitle;
    private SlothBar.Listener dragSink;
    private Runnable onPick;
    private View shelf;
    private EditText tabFilter;
    private TextView site;
    private View siteDot;
    private View shelfLabel;
    private TextView tabsStat;
    private TextView noteStat;
    /** Widget tiles by kind, and the kinds the owner has put away. */
    private final java.util.LinkedHashMap<String, LinearLayout> widgetTiles =
            new java.util.LinkedHashMap<>();
    private final java.util.LinkedHashSet<String> hiddenWidgets = new java.util.LinkedHashSet<>();

    SlothBoard(Activity activity) {
        this.activity = activity;
        this.root = new LinearLayout(activity);
        BG = SlothTheme.bg();
        CARD = SlothTheme.card();
        INK = SlothTheme.ink();
        DIM = SlothTheme.dim();
        ACCENT = SlothTheme.accent();
        ACCENT_INK = SlothTheme.accentInk();
        try {
            java.util.Set<String> stored = activity.getSharedPreferences("slothworks", Context.MODE_PRIVATE)
                    .getStringSet("hiddenWidgets", null);
            if (stored != null) {
                hiddenWidgets.addAll(stored);
            }
        } catch (Throwable ignored) {
        }
        root.setOrientation(LinearLayout.VERTICAL);
        root.setTag("board");
        build();
    }

    View view() {
        return root;
    }

    /** Told when a tab is chosen, so the drawer can close itself. */
    /** The drawer's head line: where this tab actually is. */
    void onSite(String address) {
        if (site == null) {
            return;
        }
        String shown = address == null || address.length() == 0 ? "No page open" : address;
        if (shown.contentEquals(site.getText()) && siteDot != null && siteDot.getBackground() != null) {
            return; // nothing changed: the timer asks every 800 ms
        }
        site.setText(shown);
        site.setContentDescription("Current page: " + shown + ". Tap to copy the address");
        if (subTitle != null) {
            String host = shown;
            int scheme = host.indexOf("://");
            if (scheme >= 0) {
                host = host.substring(scheme + 3);
            }
            int slash = host.indexOf('/');
            if (slash > 0) {
                host = host.substring(0, slash);
            }
            boolean none = host.length() == 0 || host.startsWith("No page");
            subTitle.setText(none ? "DASHBOARD" : host.toUpperCase(Locale.UK));
        }
        if (site.getParent() instanceof View) {
            ((View) site.getParent()).setContentDescription("Current page: " + shown
                    + ". Tap to copy the address");
        }
        if (siteDot != null) {
            int colour = SlothTheme.muted();
            if (shown.startsWith("https://")) {
                colour = SlothTheme.accent();
            } else if (shown.startsWith("http://")) {
                colour = 0xFFE2A05A;
            }
            android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
            dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dot.setColor(colour);
            siteDot.setBackground(dot);
        }
    }

    /** Put the address on the clipboard, and say so - silence after a tap reads as a bug. */
    private void copyAddress(Activity activity) {
        CharSequence value = site == null ? null : site.getText();
        if (value == null || value.length() == 0 || "No page open".contentEquals(value)) {
            return;
        }
        try {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) activity.getSystemService(
                            android.content.Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Address", value));
                android.widget.Toast.makeText(activity, "Address copied",
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable t) {
            SlothLog.warn("could not copy the address: " + t.getClass().getSimpleName());
        }
    }

    void onTabPicked(Runnable action) {
        onPick = action;
    }

    /* ------------------------------------------------------------------ build */
    private void build() {
        // No masthead. The bar above this panel is the header of the dashboard - it carries
        // the address, the controls and the way out - so a second title with its own close
        // button was the clearest remaining sign that these were two surfaces pretending to
        // be one.
        root.setTag("board");

        site = text("", 12f, DIM, false);
        site.setSingleLine(true);
        site.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams siteLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        site.setLayoutParams(siteLp);

        // a dot and the address: the dot says whether the connection is encrypted, the row
        // copies the address, which is the thing people actually want from a URL they can see
        siteDot = new View(activity);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(6), dp(6));
        dotLp.rightMargin = dp(7);
        dotLp.gravity = Gravity.CENTER_VERTICAL;
        siteDot.setLayoutParams(dotLp);

        final LinearLayout siteRow = new LinearLayout(activity);
        siteRow.setOrientation(LinearLayout.HORIZONTAL);
        siteRow.setGravity(Gravity.CENTER_VERTICAL);
        siteRow.setTag("site-row");
        siteRow.setClickable(true);
        siteRow.setFocusable(true);
        siteRow.setPadding(0, dp(2), 0, dp(2));
        siteRow.addView(siteDot);
        siteRow.addView(site);
        LinearLayout.LayoutParams siteRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        siteRowLp.bottomMargin = dp(8);
        siteRow.setLayoutParams(siteRowLp);
        siteRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyAddress(activity);
            }
        });
        root.addView(siteRow);
        root.addView(tabFilter());
        root.addView(tabHeader());
        root.addView(tabStrip());
        afterTabs = actions();
        addFull(root, afterTabs);
        shelfLabel = label("WIDGETS", true);
        shelf = widgets();
        root.addView(shelfLabel);
        root.addView(shelf);
    }

    /** The shell hands the drawer the same drag rules the bar uses. */
    void setDragSink(SlothBar.Listener sink) {
        this.dragSink = sink;
    }

    private View header() {
        final SlothBar wrap = new SlothBar(activity);
        wrap.setTag("head-drag");
        wrap.setListener(new SlothBar.Listener() {
            @Override
            public void onDragStart(float y, float carry) {
                if (dragSink != null) dragSink.onDragStart(y, carry);
            }

            @Override
            public void onDrag(float dy) {
                if (dragSink != null) dragSink.onDrag(dy);
            }

            @Override
            public boolean onSwipe(float dx) {
                // the header sits inside the sheet: a sideways flick here is the sheet's
                // own scroller's business, not a tab change
                return false;
            }

            @Override
            public void onRelease(float velocity) {
                if (dragSink != null) dragSink.onRelease(velocity);
            }

            @Override
            public void onTap() {
                // a tap on the head is not a drawer gesture
            }
        });
        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, dp(14), 0, dp(6));

        LinearLayout mark = new LinearLayout(activity);
        mark.setOrientation(LinearLayout.HORIZONTAL);
        mark.setGravity(Gravity.CENTER_VERTICAL);

        TextView avatar = text("\uD83E\uDD9C", 15f, INK, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(outline(999, CARD, SlothTheme.hairline(0x33)));
        avatar.setContentDescription("Sloth");
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dp(34), dp(34));
        avLp.rightMargin = dp(10);
        mark.addView(avatar, avLp);

        TextView word = text("Sloth", 22f, INK, true);
        // the second line is the page, because that is the fact you came here to see;
        // the product name is already on the first line
        subTitle = text("DASHBOARD", 11f, DIM, true);
        subTitle.setLetterSpacing(0.10f);
        subTitle.setSingleLine(true);
        subTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.leftMargin = dp(8);
        subLp.bottomMargin = dp(3);
        mark.addView(word, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mark.addView(subTitle, subLp);
        head.addView(mark, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // a 44 dp target with a 16 dp cross inside it: a drawn icon has no intrinsic
        // size, so handing it a wrap_content slot let it stretch into a wide empty capsule
        LinearLayout closeButton = new LinearLayout(activity);
        closeButton.setGravity(Gravity.CENTER);
        closeButton.setBackground(outline(999, 0, SlothTheme.hairline(0x33)));
        closeButton.setContentDescription("Close the dashboard");
        closeButton.setClickable(true);
        closeButton.setFocusable(true);
        SlothIcons cross = SlothIcons.of(activity, SlothIcons.Kind.CLOSE, 14, INK);
        // the button speaks for itself; a clickable child would be a second, silent control
        cross.setClickable(false);
        cross.setFocusable(false);
        closeButton.addView(cross, new LinearLayout.LayoutParams(dp(14), dp(14)));
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDrawer();
            }
        });
        head.addView(closeButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
        wrap.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    /**
     * The browser's own tab strip, mirrored. Titles come from the chip's TextView,
     * and the row is rebuilt when a title changes rather than only when a tab is added.
     */
    private static final int FILTER_AT = 6;

    /** Only worth the space once there are enough tabs to lose one in. */
    private View tabFilter() {
        tabFilter = new EditText(activity);
        tabFilter.setSingleLine(true);
        tabFilter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        tabFilter.setTextColor(INK);
        tabFilter.setHintTextColor(DIM);
        tabFilter.setHint("Find a tab");
        tabFilter.setContentDescription("Find a tab");
        tabFilter.setBackground(outline(14, CARD, SlothTheme.hairline(0x33)));
        tabFilter.setPadding(dp(12), dp(8), dp(12), dp(8));
        tabFilter.setVisibility(View.GONE);
        tabFilter.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
                applyFilter(s.toString());
            }

            public void afterTextChanged(android.text.Editable s) {
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        tabFilter.setLayoutParams(lp);
        return tabFilter;
    }

    private void applyFilter(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.UK);
        for (int i = 0; i < tabRow.getChildCount(); i++) {
            View card = tabRow.getChildAt(i);
            CharSequence label = cardLabel(card);
            boolean match = needle.length() == 0
                    || (label != null && label.toString().toLowerCase(Locale.UK).contains(needle));
            card.setVisibility(match ? View.VISIBLE : View.GONE);
        }
    }

    /** The mock's "Tabs" section header, with the browser's own add-tab control at right. */
    private View tabHeader() {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(10), 0, 0);

        TextView title = text("Tabs", 12f, DIM, true);
        title.setLetterSpacing(0.14f);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView plus = text("+", 18f, INK, true);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(outline(999, CARD, SlothTheme.hairline(0x33)));
        plus.setContentDescription("New tab");
        plus.setClickable(true);
        plus.setFocusable(true);
        plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tap("btnPlus");
                if (onPick != null) {
                    onPick.run();
                }
            }
        });
        header.addView(plus, new LinearLayout.LayoutParams(dp(34), dp(30)));
        return header;
    }

    private View tabStrip() {
        tabRow = new LinearLayout(activity);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setPadding(0, dp(4), 0, dp(8));
        return tabRow;
    }

    /** A row that fills the width, so its weighted children actually share it. */
    private void addFull(ViewGroup parent, View child) {
        parent.addView(child, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Row that starts the section after the tab cards; the peek ends just above it. */
    private View afterTabs;


    /**
     * A height that shows the head and the whole tab row, so the smallest open drawer
     * never cuts a card in half. Zero until the board has been laid out once.
     */
    /** Measures once at a given width, so the row boundary is known before first paint. */
    int measurePeek(int widthPx) {
        try {
            view().measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        } catch (RuntimeException ignored) {
        }
        return peekHeight();
    }

    int peekHeight() {
        if (afterTabs == null || afterTabs.getTop() <= 0) {
            return 0;
        }
        return afterTabs.getTop() - dp(6);
    }

    private View actions() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, dp(12));

        // the mock's four quick actions. Home is the app's own home; the other three
        // are the app's own screens, opened the same way Settings is.
        row.addView(action("\u2302", "Home", "btnHome", false));
        row.addView(action("\u2691", "Bookmarks", "panel:bookmarks", false));
        row.addView(action("\u25F4", "History", "panel:history", false));
        row.addView(action("\u2193", "Downloads", "panel:downloads", false));
        return row;
    }

    private View action(String glyph, String name, final String target, boolean primary) {
        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(dp(4), dp(12), dp(4), dp(10));
        cell.setContentDescription(name);
        cell.setBackground(outline(18, primary ? SlothTheme.hairline(0x1A) : CARD, primary ? SlothTheme.lineAt(0x73) : SlothTheme.lineAt(0x1F)));
        cell.setClickable(true);
        cell.setFocusable(true);
        cell.setMinimumHeight(dp(84));
        cell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tap(target);
                // an action from the dashboard is the end of the errand too
                if (onPick != null) {
                    onPick.run();
                }
            }
        });

        TextView icon = text(glyph, 17f, primary ? ACCENT_INK : INK, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(outline(12, primary ? ACCENT : SlothTheme.lineAt(0x12), 0));
        cell.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));

        TextView title = text(name, 12f, INK, true);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(8);
        cell.addView(title, tlp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(8);
        cell.setLayoutParams(lp);
        return cell;
    }

    private View widgets() {
        LinearLayout grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.VERTICAL);

        LinearLayout first = row();
        first.addView(widget("clock", card("Clock", clockCard())), weight());
        first.addView(widget("calc", calculatorCard()), weight());
        grid.addView(first);

        grid.addView(calculatorPad());
        grid.addView(widget("notes", wide("Notes", notesCard())));

        // the mock's counters, with numbers that exist: how many tabs there are, and
        // whether the note is holding anything
        LinearLayout counters = row();
        counters.addView(widget("tabs", card("Tabs", counterBody("0", "open", true))), weight());
        counters.addView(widget("note", card("Note", counterBody("empty", "on this phone", false))), weight());
        addFull(grid, counters);
        return grid;
    }

    // ------------------------------------------------------------------ calculator
    private double calcValue;
    private double calcStored;
    private String calcOp = "";
    private boolean calcFresh = true;
    private TextView calcFace;
    private LinearLayout calcPad;

    private View counterBody(String value, String label, boolean tabs) {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.BOTTOM);
        TextView big = text(value, 16f, INK, true);
        if (tabs) {
            tabsStat = big;
        } else {
            noteStat = big;
        }
        body.addView(big);
        body.addView(text(label, 11f, DIM, false));
        return body;
    }

    private View calculatorCard() {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        calcFace = text("0", 22f, INK, true);
        calcFace.setSingleLine(true);
        calcFace.setEllipsize(TextUtils.TruncateAt.START);
        body.addView(calcFace);
        TextView hint = text("Tap to open", 11f, DIM, false);
        body.addView(hint);

        LinearLayout cardRoot = (LinearLayout) card("Calc", body);
        cardRoot.setClickable(true);
        cardRoot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (calcPad == null) {
                    return;
                }
                boolean showing = calcPad.getVisibility() == View.VISIBLE;
                calcPad.setVisibility(showing ? View.GONE : View.VISIBLE);
            }
        });
        return cardRoot;
    }

    private View calculatorPad() {
        calcPad = new LinearLayout(activity);
        calcPad.setOrientation(LinearLayout.VERTICAL);
        calcPad.setVisibility(View.GONE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        calcPad.setLayoutParams(lp);

        String[][] keys = {
                {"C", "\u00F7", "\u00D7", "\u2212"},
                {"7", "8", "9", "+"},
                {"4", "5", "6", "="},
                {"1", "2", "3", "."},
                {"0", "", "", ""}
        };
        for (String[] rowKeys : keys) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (final String key : rowKeys) {
                if (key.length() == 0) {
                    View spacer = new View(activity);
                    row.addView(spacer, new LinearLayout.LayoutParams(0, dp(44), 1f));
                    continue;
                }
                TextView k = text(key, 16f, INK, true);
                k.setGravity(Gravity.CENTER);
                k.setBackground(outline(12, CARD, SlothTheme.hairline(0x33)));
                k.setContentDescription("Calculator " + key);
                k.setLayoutParams(new LinearLayout.LayoutParams(0, dp(44), 1f));
                k.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        calcKey(key);
                    }
                });
                row.addView(k);
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = dp(6);
            row.setLayoutParams(rlp);
            calcPad.addView(row);
        }
        return calcPad;
    }

    private void calcKey(String key) {
        if ("C".equals(key)) {
            calcValue = 0;
            calcStored = 0;
            calcOp = "";
            calcFresh = true;
            show("0");
            return;
        }
        if ("=".equals(key)) {
            calcStored = apply(calcStored, calcValue, calcOp);
            calcValue = calcStored;
            calcOp = "";
            calcFresh = true;
            show(fmt(calcStored));
            return;
        }
        if ("+".equals(key) || "\u2212".equals(key) || "\u00D7".equals(key) || "\u00F7".equals(key)) {
            calcStored = apply(calcStored, calcValue, calcOp);
            calcValue = calcStored;
            calcOp = key;
            calcFresh = true;
            show(fmt(calcStored));
            return;
        }
        if (".".equals(key)) {
            if (calcFresh) {
                calcValue = 0;
                calcFresh = false;
                show("0.");
                return;
            }
            String current = fmt(calcValue);
            if (!current.contains(".")) {
                show(current + ".");
            }
            return;
        }
        // digit
        if (calcFresh) {
            calcValue = Double.parseDouble(key);
            calcFresh = false;
        } else {
            String current = fmt(calcValue);
            if ("0".equals(current)) {
                current = "";
            }
            if (current.contains(".") && current.length() - current.indexOf('.') > 2) {
                return;
            }
            calcValue = Double.parseDouble(current + key);
        }
        show(fmt(calcValue));
    }

    private double apply(double a, double b, String op) {
        if ("+".equals(op)) {
            return a + b;
        }
        if ("\u2212".equals(op)) {
            return a - b;
        }
        if ("\u00D7".equals(op)) {
            return a * b;
        }
        if ("\u00F7".equals(op)) {
            return b == 0 ? 0 : a / b;
        }
        return b;
    }

    private void show(String value) {
        if (calcFace != null) {
            calcFace.setText(value);
            calcFace.setContentDescription("Calculator shows " + value);
        }
    }

    private static String fmt(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return String.valueOf((long) value);
        }
        String s = String.valueOf(Math.round(value * 10000d) / 10000d);
        return s;
    }

    /**
     * Register a widget tile and give it the one control edit mode actually owns:
     * Hide / Show. Reordering was advertised and never built, so it is not claimed here.
     */
    private LinearLayout widget(String kind, View view) {
        LinearLayout tile = (LinearLayout) view;
        widgetTiles.put(kind, tile);

        applyWidgetVisibility();
        return tile;
    }

    private void persistWidgets() {
        try {
            activity.getSharedPreferences("slothworks", Context.MODE_PRIVATE)
                    // committed, not applied: which widgets the owner put away is a choice,
                    // and a choice lost to a process kill is a choice they have to make twice
                    .edit().putStringSet("hiddenWidgets",
                            new java.util.LinkedHashSet<>(hiddenWidgets)).commit();
        } catch (Throwable ignored) {
        }
    }

    /** A widget the chooser has switched off is gone; the drawer rebuilds around it. */
    void applyWidgetVisibility() {
        for (java.util.Map.Entry<String, LinearLayout> entry : widgetTiles.entrySet()) {
            String kind = entry.getKey();
            boolean hidden = hiddenWidgets.contains(kind);
            entry.getValue().setVisibility(hidden ? View.GONE : View.VISIBLE);
        }
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        return row;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.rightMargin = dp(8);
        return lp;
    }

    /** A tile: label, chevron, body. Consistent affordance across every card. */
    private View card(String name, View body) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(14), dp(12), dp(14), dp(12));
        tile.setBackground(outline(SlothTheme.radius(), CARD, SlothTheme.hairline(0x33)));
        tile.setContentDescription(name);
        // only a tile that does something may claim to be clickable: TalkBack reads
        // setClickable(true) as "this responds", and the Clock card did not
        tile.setClickable(false);
        tile.setFocusable(false);

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text(name.toUpperCase(Locale.UK), 12f, DIM, true);
        label.setLetterSpacing(0.14f);
        top.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(text("\u203A", 15f, DIM, false));
        tile.addView(top);

        tile.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return tile;
    }

    private View wide(String name, View body) {
        LinearLayout tile = (LinearLayout) card(name, body);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.bottomMargin = dp(8);
        tile.setLayoutParams(lp);
        return tile;
    }

    /* ---------------------------------------------------------------- content */
    private View clockCard() {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.BOTTOM);

        clock = text(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()), 28f, INK, true);
        body.addView(clock);
        TextView date = text(new SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(new Date()),
                12f, DIM, false);
        body.addView(date);

        SlothShell.every(30000, new Runnable() {
            @Override
            public void run() {
                if (clock != null) {
                    clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
                }
            }
        });
        return body;
    }

    private View notesCard() {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);

        final EditText note = new EditText(activity);
        note.setBackground(null);
        note.setTextColor(INK);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        note.setGravity(Gravity.TOP);
        note.setPadding(0, dp(6), 0, 0);
        note.setMinLines(2);
        note.setHintTextColor(DIM);
        note.setHint("Write something. It stays on the phone.");
        note.setContentDescription("Notes");
        // an EditText asks its ancestors not to intercept while it handles a gesture.
        // That would make the note a dead zone for both scrolling and closing, so the
        // request is withdrawn on touch-down: taps still land, drags still move the drawer.
        note.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                    android.view.ViewParent parent = v.getParent();
                    while (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(false);
                        parent = parent.getParent();
                    }
                }
                return false;
            }
        });
        try {
            note.setText(activity.getSharedPreferences("slothworks", Context.MODE_PRIVATE)
                    .getString("note", ""));
        } catch (Throwable ignored) {
        }
        note.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
                try {
                    activity.getSharedPreferences("slothworks", Context.MODE_PRIVATE)
                            .edit().putString("note", s.toString()).apply();
                } catch (Throwable ignored) {
                }
            }

            public void afterTextChanged(android.text.Editable s) {
            }
        });
        body.addView(note, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout meta = new LinearLayout(activity);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.addView(text("Saved on this phone", 11.5f, DIM, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView newNote = text("+ New note", 12f, ACCENT, true);
        newNote.setContentDescription("New note");
        newNote.setPadding(dp(13), dp(7), dp(13), dp(7));
        newNote.setBackground(outline(99, 0, SlothTheme.hairline(0x33)));
        newNote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                note.setText("");
            }
        });
        meta.addView(newNote);
        body.addView(meta);
        return body;
    }

    /* ------------------------------------------------------------------ input */
    private View label(String value, boolean withEdit) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(8));

        TextView t = text(value, 12f, SlothTheme.muted(), true);
        t.setLetterSpacing(0.16f);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (withEdit) {
            editButton = text("Edit widgets", 13f, ACCENT, true);
            editButton.setContentDescription("Edit widgets");
            editButton.setContentDescription("Edit widgets");
            editButton.setGravity(Gravity.CENTER);
            editButton.setPadding(dp(16), 0, dp(16), 0);
            editButton.setMinHeight(dp(40));
            editButton.setBackground(outline(999, 0, SlothTheme.hairline(0x33)));
            editButton.setContentDescription("Edit widgets");
            editButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (onEditWidgets != null) {
                        onEditWidgets.run();
                    }
                }
            });
            row.addView(editButton, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        }
        return row;
    }

    /** Pocket height shows tabs and actions; the shelf belongs to the taller state. */
    /** Tabs and notes are the two numbers the shell can honestly know. */
    void onTabCount(int count) {
        if (tabsStat != null) {
            tabsStat.setText(count == 1 ? "1 tab" : count + " tabs");
        }
    }

    /**
     * Bring the drawer's cards in one after another.
     *
     * <p>A sheet that appears all at once reads as a screenshot; the same sheet arriving in
     * a short sequence reads as something that opened. Each card starts a little low and
     * clear of its colour, then settles - and the whole thing is a handful of frames, not
     * an animation system.</p>
     */
    void reveal() {
        final ViewGroup body = root;
        if (body == null || body.getChildCount() == 0) {
            return;
        }
        final int count = body.getChildCount();
        final int rise = dp(body.getContext(), 10);
        for (int i = 0; i < count; i++) {
            View child = body.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(rise);
        }
        final int[] frame = {0};
        final int frames = count + 7;
        // a close or a rebuild cancels the entrance; the cards must be left *in*, not
        // abandoned half-transparent behind a sheet that is already gone
        final int started = SlothShell.generation();
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (SlothShell.generation() != started) {
                    settleNow();
                    return;
                }
                frame[0]++;
                for (int i = 0; i < count; i++) {
                    View child = body.getChildAt(i);
                    float t = Math.max(0f, Math.min(1f, (frame[0] - i) / 7f));
                    child.setAlpha(t);
                    child.setTranslationY(rise * (1f - t));
                }
                if (frame[0] < frames) {
                    SlothShell.postDelayed(this, 16);
                } else {
                    // the last frame states the finished values rather than approaching them
                    settleNow();
                }
            }
        };
        SlothShell.post(tick);
    }

    /** Leave every card fully arrived: the only honest end for an entrance. */
    private void settleNow() {
        if (root == null) {
            return;
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            root.getChildAt(i).setAlpha(1f);
            root.getChildAt(i).setTranslationY(0f);
        }
    }

    private static int dp(android.content.Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    void setCompact(boolean compact) {
        if (shelf != null) {
            shelf.setVisibility(compact ? View.GONE : View.VISIBLE);
        }
        if (shelfLabel != null) {
            shelfLabel.setVisibility(compact ? View.GONE : View.VISIBLE);
        }
    }

    /** The widgets the drawer can show, in the order the boards present them. */
    static final String[] KINDS = {"clock", "calc", "notes", "tabs", "note"};

    static String kindLabel(String kind) {
        if ("clock".equals(kind)) {
            return "Clock";
        }
        if ("calc".equals(kind)) {
            return "Calculator";
        }
        if ("notes".equals(kind)) {
            return "Notes";
        }
        if ("tabs".equals(kind)) {
            return "Tab count";
        }
        return "Note state";
    }

    boolean isHidden(String kind) {
        return hiddenWidgets.contains(kind);
    }

    /** Flip one widget on or off and remember it; the shell rebuilds to show the result. */
    void toggleWidget(String kind) {
        if (!hiddenWidgets.remove(kind)) {
            hiddenWidgets.add(kind);
        }
        persistWidgets();
        applyWidgetVisibility();
    }

    /** Where "Edit widgets" leads: the shell's chooser, the one place widgets change. */
    private Runnable onEditWidgets;

    void setEditSink(Runnable sink) {
        onEditWidgets = sink;
    }




    /* ----------------------------------------------------------------- mirror */
    /** Bring the tab strip in line with the browser's own chips. */
    void mirror(Activity activity) {
        View tabs = SlothShell.resolveStrip(activity);
        if (!(tabs instanceof ViewGroup)) {
            return;
        }
        ViewGroup strip = (ViewGroup) tabs;
        if (tabRow.getChildCount() == strip.getChildCount() && strip.getChildCount() > 0
                && !stale(strip)) {
            return;
        }
        boolean many = strip.getChildCount() > FILTER_AT;
        if (tabFilter != null) {
            tabFilter.setVisibility(many ? View.VISIBLE : View.GONE);
        }
        tabRow.removeAllViews();
        int active = -1;
        for (int i = 0; i < strip.getChildCount(); i++) {
            View candidate = strip.getChildAt(i);
            if (candidate.isSelected() || candidate.isActivated()) {
                active = i;
                break;
            }
        }
        for (int i = 0; i < strip.getChildCount(); i++) {
            final View chip = strip.getChildAt(i);
            CharSequence title = readableTitle(SlothShell.label(chip), i + 1);
            final boolean current = i == active;
            // the mock's tab cards: a thumbnail with the page's initial, its title
            // underneath, and its own close control - three across, like the concept
            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(8), dp(8), dp(8), dp(6));
            card.setBackground(outline(16, current ? SlothTheme.lineAt(0x26) : CARD,
                    current ? ACCENT : SlothTheme.hairline(0x33)));
            if (current) {
                // the tab you are on should be visible across the room, not by a hairline
                ((android.graphics.drawable.GradientDrawable) card.getBackground())
                        .setStroke(dp(2), ACCENT);
            }
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0, dp(108), 1f);
            cardLp.rightMargin = dp(8);
            card.setLayoutParams(cardLp);

            if (current) {
                // the accent cap: the tab you are on reads instantly, whatever the palette
                View cap = new View(activity);
                GradientDrawable capBg = new GradientDrawable();
                capBg.setCornerRadius(dp(2));
                capBg.setColor(ACCENT);
                cap.setBackground(capBg);
                LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(
                        dp(18), dp(3));
                capLp.bottomMargin = dp(6);
                capLp.gravity = Gravity.CENTER_HORIZONTAL;
                card.addView(cap, capLp);
            }

            LinearLayout thumb = new LinearLayout(activity);
            thumb.setGravity(Gravity.CENTER);
            thumb.setBackground(outline(12, SlothTheme.lineAt(0x14), 0));
            TextView initial = text(String.valueOf(title.toString().trim().isEmpty()
                    ? "?" : title.toString().trim().toUpperCase(Locale.UK).charAt(0)), 22f, DIM, true);
            thumb.addView(initial);
            card.addView(thumb, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            LinearLayout titleRow = new LinearLayout(activity);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            titleRow.setPadding(0, dp(4), 0, 0);

            TextView label = text(title.toString(), 11f, current ? INK : DIM, current);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            titleRow.addView(label, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            final View closeTarget = SlothShell.byTagDeep(chip, "close");
            TextView close = text("\u00D7", 14f, DIM, false);
            close.setGravity(Gravity.CENTER);
            close.setContentDescription("Close " + title);
            close.setLayoutParams(new LinearLayout.LayoutParams(dp(24), dp(24)));
            close.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (closeTarget != null) {
                        closeTarget.performClick();
                    } else {
                        chip.performClick();
                    }
                    mirrorNow();
                }
            });
            titleRow.addView(close);
            card.addView(titleRow);

            card.setTag("tab-card");
            card.setContentDescription(current
                    ? title + ", current tab (switch to it)"
                    : "Switch to " + title);
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    chip.performClick();
                    if (onPick != null) {
                        onPick.run();
                    }
                }
            });
            tabRow.addView(card, cardLp);
        }
        if (tabFilter != null) {
            applyFilter(tabFilter.getText().toString());
        }
    }

    
    
    
    
    void mirrorNow() {
        tabRow.removeAllViews();
        View tabs = SlothShell.resolveStrip(activity);
        if (tabs instanceof ViewGroup) {
            mirror(activity);
        }
    }

    /** A card's own text lives inside it; casting the card to TextView was the bug. */
    /** The card's title. A card also holds the page's initial, so the longest text wins. */
    /**
     * The browser names an untitled tab with its accessibility description - "Tabs, 1" -
     * and a loaded page with its title. Neither is what a card should say, so a bare
     * number becomes "Tab 1" and the spoken prefix is dropped.
     */
    private static CharSequence readableTitle(CharSequence raw, int position) {
        String text = raw == null ? "" : raw.toString().trim();
        text = text.replaceFirst("(?i)^tabs?\\s*,\\s*", "").trim();
        if (text.isEmpty()) {
            return "Tab " + position;
        }
        if (text.matches("\\d+")) {
            return "Tab " + text;
        }
        return text;
    }

    private static CharSequence cardLabel(View card) {
        if (card instanceof TextView) {
            return ((TextView) card).getText();
        }
        CharSequence best = null;
        if (card instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) card;
            for (int i = 0; i < g.getChildCount(); i++) {
                CharSequence found = cardLabel(g.getChildAt(i));
                if (found != null && found.length() > (best == null ? 0 : best.length())) {
                    best = found;
                }
            }
        }
        return best;
    }

    private boolean stale(ViewGroup strip) {
        if (strip.getChildCount() != tabRow.getChildCount()) {
            return true;
        }
        for (int i = 0; i < strip.getChildCount(); i++) {
            CharSequence want = SlothShell.label(strip.getChildAt(i));
            CharSequence have = cardLabel(tabRow.getChildAt(i));
            if (want != null && want.length() > 0 && have != null
                    && !want.toString().contentEquals(have)) {
                return true;
            }
        }
        return false;
    }

    /* ------------------------------------------------------------------ utils */
    /**
     * Open one of the app's own widgets by kind.
     *
     * SquareFace.press looks like the entry point and is not: it is the
     * calculator's key handler, so press("calc") just feeds a key the calculator
     * ignores. toggle(kind) is what expands a widget, and it needs the app's
     * drawer to exist - so the panel is released first.
     */
    private void closeDrawer() {
        try {
            Class<?> face = Class.forName("com.sloth.browser.DrawerFace");
            java.lang.reflect.Method m = face.getMethod("closeDrawer", Activity.class);
            m.invoke(null, activity);
        } catch (Throwable ignored) {
        }
        if (onPick != null) {
            onPick.run();
        }
    }

    /**
     * There is no close-tab binding in this app: each tab chip carries its own close
     * control, tagged "close". Closing the current tab means finding the active chip
     * and pressing that control, the same path the tab cards use.
     */
    private void closeCurrentTab() {
        View strip = SlothShell.resolveStrip(activity);
        if (!(strip instanceof ViewGroup)) {
            return;
        }
        ViewGroup tabs = (ViewGroup) strip;
        View chip = null;
        for (int i = 0; i < tabs.getChildCount(); i++) {
            View candidate = tabs.getChildAt(i);
            if (candidate.isSelected() || candidate.isActivated()) {
                chip = candidate;
                break;
            }
        }
        if (chip == null && tabs.getChildCount() > 0) {
            chip = tabs.getChildAt(tabs.getChildCount() - 1);
        }
        if (chip == null) {
            return;
        }
        View closer = SlothShell.byTagDeep(chip, "close");
        if (closer != null) {
            closer.performClick();
        }
        mirrorNow();
    }

    private void tap(String bindingName) {
        if (bindingName != null && bindingName.startsWith("panel:")) {
            SlothShell.openPanel(activity, bindingName.substring(6));
            return;
        }
        if ("close:current".equals(bindingName)) {
            closeCurrentTab();
            return;
        }
        // the app's menu and add-ons screens draw into its panel, which the shell now keeps
        // shut - it was the empty box over the page. Menu is the dashboard; add-ons is the
        // page that carries the engine's own switches.
        if ("btnMenu".equals(bindingName)) {
            SlothShell.openDrawer(activity);
            return;
        }
        if ("btnExt".equals(bindingName)) {
            SlothShell.openSettingsPage(activity);
            return;
        }
        View v = SlothShell.resolve(activity, bindingName);
        if (v != null) {
            v.performClick();
        }
    }

    private TextView text(String value, float size, int colour, boolean bold) {
        TextView t = new TextView(activity);
        t.setText(value);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTextColor(colour);
        if (bold) {
            t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return t;
    }

    /**
     * Cards are glass, not paint: a shallow gradient from a slightly lifted top edge
     * down to the card colour, then a hairline. Cheap, and it reads as depth without
     * a blur the platform cannot do below API 31.
     */
    private GradientDrawable outline(float radius, int fill, int stroke) {
        if (fill == CARD) {
            // translucent on the dark palettes so the forest shows through the glass
            boolean clear = SlothTheme.frosted();
            GradientDrawable glass = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{clear ? (0xF2 << 24 | (SlothTheme.cardTop() & 0xFFFFFF)) : SlothTheme.cardTop(),
                            clear ? (0xEC << 24 | (CARD & 0xFFFFFF)) : CARD});
            glass.setCornerRadius(dp((int) radius));
            if (stroke != 0) {
                glass.setStroke(Math.max(1, dp(1)), stroke);
            }
            return glass;
        }
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp((int) radius));
        d.setColor(fill);
        if (stroke != 0) {
            d.setStroke(Math.max(1, dp(1)), stroke);
        }
        return d;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
