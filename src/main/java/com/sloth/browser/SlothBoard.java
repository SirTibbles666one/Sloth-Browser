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
 * The drawer's content, in the order you reach for it: where you are, your tabs, the places
 * the browser can take you, and the widgets - folded away until asked for.
 *
 * <p>Built in code from the theme's tokens. Every control is labelled for TalkBack, every
 * text size is sp, and every action forwards to the browser's own bound widget with
 * {@code performClick()} so tabs, history and the updater keep the app's behaviour.</p>
 */
final class SlothBoard {

    /** More tabs than this and the drawer offers a field to find one. */
    static final int FILTER_AT = 6;

    /** The widgets the drawer can show, in the order it shows them. */
    static final String[] KINDS = {"clock", "calc", "notes", "tabs", "note"};

    private static final String PREFS = "slothworks";

    private final Activity activity;
    private final LinearLayout root;

    private TextView site;
    private View siteDot;
    private TextView tabCountBadge;
    private EditText tabFilter;
    private SlothGrid tabGrid;
    private SlothGrid shortcutGrid;
    private View backCell;
    private View forwardCell;
    private View widgetHeader;
    private SlothIcons widgetChevron;
    private TextView editButton;
    private LinearLayout widgetBody;
    private boolean widgetsOpen;

    private Runnable onPick;
    private Runnable onEditWidgets;
    private Runnable onResize;

    private TextView clock;
    private TextView tabsStat;
    private TextView noteStat;

    /** Every view a widget owns, by kind, so hiding the calculator takes its keypad too. */
    private final java.util.LinkedHashMap<String, java.util.ArrayList<View>> widgetViews =
            new java.util.LinkedHashMap<>();
    private final java.util.LinkedHashMap<String, View> widgetTiles = new java.util.LinkedHashMap<>();
    private final java.util.LinkedHashSet<String> hiddenWidgets = new java.util.LinkedHashSet<>();
    /** Views this class hid, so showing a widget again restores only what it took away. */
    private final java.util.HashSet<View> hiddenByUs = new java.util.HashSet<>();

    SlothBoard(Activity activity) {
        this.activity = activity;
        this.root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setTag("board");
        root.setPadding(0, dp(2), 0, dp(16));
        try {
            android.content.SharedPreferences prefs =
                    activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            java.util.Set<String> stored = prefs.getStringSet("hiddenWidgets", null);
            if (stored != null) {
                hiddenWidgets.addAll(stored);
            }
            widgetsOpen = prefs.getBoolean("widgetsOpen", false);
        } catch (Throwable ignored) {
            SlothLog.swallow("SlothBoard.SlothBoard", ignored);
            // no preferences: every widget shown, the shelf folded
        }
        build();
    }

    View view() {
        return root;
    }

    /** How many sections the drawer has; the entrance has to reach every one. */
    int cardCount() {
        return root.getChildCount();
    }

    void onTabPicked(Runnable action) {
        onPick = action;
    }

    /** Told when the drawer's content changes height on its own, so the sheet can follow. */
    void onResize(Runnable action) {
        onResize = action;
    }

    /** Where "Edit" leads: the shell's chooser, the one place widgets change. */
    void setEditSink(Runnable sink) {
        onEditWidgets = sink;
    }

    /* ------------------------------------------------------------------ build */
    private void build() {
        root.addView(head());
        root.addView(sectionHeader("Tabs", true));
        root.addView(tabFilter());
        // two columns leave a title a few letters on a small phone or at large text, and a
        // word broken across lines in the middle; one column gives it the drawer's width
        tabGrid = new SlothGrid(activity, roomyTabs(activity) ? 2 : 1, dp(10));
        tabGrid.setTag("tab-row");
        tabGrid.setPadding(0, dp(2), 0, dp(4));
        root.addView(tabGrid, full());
        root.addView(sectionHeader("Go to", false));
        root.addView(shortcuts(), full());
        root.addView(widgetHeader());
        root.addView(widgets(), full());
        applyWidgetsOpen();
    }

    private LinearLayout.LayoutParams full() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /**
     * Where the page is, and the way out. The row copies the address - the thing people want
     * from a URL they can see - and the dot says whether the connection is encrypted.
     */
    private View head() {
        // no drag wrapper: the head sits in the scrolling list, which already hands a pull
        // past its top to the sheet - a second handler would fight it for the same gesture
        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, dp(6), 0, dp(4));

        LinearLayout siteRow = new LinearLayout(activity);
        siteRow.setOrientation(LinearLayout.HORIZONTAL);
        siteRow.setGravity(Gravity.CENTER_VERTICAL);
        siteRow.setTag("site-row");
        siteRow.setClickable(true);
        siteRow.setFocusable(true);
        siteRow.setMinimumHeight(dp(48));
        siteDot = new View(activity);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotLp.rightMargin = dp(10);
        siteRow.addView(siteDot, dotLp);
        site = text("", 13f, SlothTheme.dim(), false);
        site.setMaxLines(1);
        site.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        siteRow.addView(site, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        siteRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyAddress();
            }
        });
        head.addView(siteRow, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        View close = roundButton(SlothIcons.Kind.CLOSE, "Close the drawer", 15,
                SlothTheme.lineAt(0x14));
        close.setTag("board-close");
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDrawer();
            }
        });
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        closeLp.leftMargin = dp(8);
        head.addView(close, closeLp);
        onSite(null);
        return head;
    }

    /** The drawer's head line: where this tab is. Unchanged text is not rewritten. */
    void onSite(String address) {
        if (site == null) {
            return;
        }
        String shown = address == null || address.length() == 0 ? "No page open" : address;
        if (shown.contentEquals(site.getText()) && siteDot.getBackground() != null) {
            return;
        }
        site.setText(shown);
        String spoken = "No page open".equals(shown)
                ? "No page open"
                : "Current page: " + shown + ". Tap to copy the address";
        site.setContentDescription(spoken);
        if (site.getParent() instanceof View) {
            ((View) site.getParent()).setContentDescription(spoken);
        }
        int colour = SlothTheme.muted();
        if (shown.startsWith("https://")) {
            colour = SlothTheme.accent();
        } else if (shown.startsWith("http://")) {
            colour = 0xFFE2A05A;
        }
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(colour);
        siteDot.setBackground(dot);
    }

    private void copyAddress() {
        CharSequence value = site == null ? null : site.getText();
        if (value == null || value.length() == 0 || "No page open".contentEquals(value)) {
            return;
        }
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                    activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Address", value));
                android.widget.Toast.makeText(activity, "Address copied",
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable t) {
            SlothLog.warn("could not copy the address: " + t.getClass().getSimpleName());
        }
    }

    /**
     * A section's title. The tabs section also carries how many there are and the way to
     * open another, because those are the two things you want next to a list of tabs.
     */
    private View sectionHeader(String title, boolean tabs) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(tabs ? 8 : 18), 0, dp(8));
        header.setMinimumHeight(dp(tabs ? 52 : 44));

        TextView label = text(title, 15f, SlothTheme.ink(), true);
        header.addView(label);
        if (!tabs) {
            return header;
        }
        tabCountBadge = text("0", 12f, SlothTheme.dim(), true);
        tabCountBadge.setTag("tab-count-badge");
        tabCountBadge.setGravity(Gravity.CENTER);
        tabCountBadge.setMinWidth(dp(24));
        tabCountBadge.setPadding(dp(7), dp(1), dp(7), dp(1));
        tabCountBadge.setBackground(fill(999, SlothTheme.lineAt(0x1A), 0));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeLp.leftMargin = dp(8);
        header.addView(tabCountBadge, badgeLp);
        header.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));

        LinearLayout newTab = new LinearLayout(activity);
        newTab.setTag("new-tab");
        newTab.setOrientation(LinearLayout.HORIZONTAL);
        newTab.setGravity(Gravity.CENTER);
        newTab.setPadding(dp(12), 0, dp(14), 0);
        newTab.setMinimumHeight(dp(48));
        newTab.setBackground(fill(999, SlothTheme.accent(), 0));
        newTab.setContentDescription("New tab");
        newTab.setClickable(true);
        newTab.setFocusable(true);
        newTab.addView(SlothIcons.decorative(activity, SlothIcons.Kind.PLUS, 14,
                SlothTheme.accentInk()), new LinearLayout.LayoutParams(dp(14), dp(14)));
        TextView word = text("New tab", 13f, SlothTheme.accentInk(), true);
        word.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams wordLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wordLp.leftMargin = dp(6);
        newTab.addView(word, wordLp);
        newTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tap("btnPlus");
                picked();
            }
        });
        header.addView(newTab, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        return header;
    }

    private View tabFilter() {
        tabFilter = new EditText(activity);
        tabFilter.setTag("tab-filter");
        tabFilter.setSingleLine(true);
        tabFilter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        tabFilter.setTextColor(SlothTheme.ink());
        tabFilter.setHintTextColor(SlothTheme.dim());
        tabFilter.setHint("Find a tab");
        tabFilter.setContentDescription("Find a tab");
        tabFilter.setBackground(fill(14, SlothTheme.field(), SlothTheme.hairline(0x33)));
        tabFilter.setPadding(dp(14), dp(10), dp(14), dp(10));
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
        LinearLayout.LayoutParams lp = full();
        lp.bottomMargin = dp(10);
        tabFilter.setLayoutParams(lp);
        return tabFilter;
    }

    private void applyFilter(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.UK);
        for (int i = 0; i < tabGrid.getChildCount(); i++) {
            View card = tabGrid.getChildAt(i);
            Object title = card.getTag(R_TITLE);
            boolean match = needle.length() == 0 || (title != null
                    && title.toString().toLowerCase(Locale.UK).contains(needle));
            card.setVisibility(match ? View.VISIBLE : View.GONE);
        }
    }

    /* -------------------------------------------------------------- shortcuts */
    /**
     * Eight labelled places, four across. Back and forward live here now that the bar is a
     * search field first; they dim when the browser says there is nowhere to go.
     */
    private View shortcuts() {
        shortcutGrid = new SlothGrid(activity, 4, dp(8));
        shortcutGrid.setTag("shortcuts");
        backCell = shortcut(SlothIcons.Kind.BACK, "Back", "btnBack");
        forwardCell = shortcut(SlothIcons.Kind.FORWARD, "Forward", "btnFwd");
        shortcutGrid.addView(backCell);
        shortcutGrid.addView(forwardCell);
        shortcutGrid.addView(shortcut(SlothIcons.Kind.HOME, "Home", "btnHome"));
        shortcutGrid.addView(shortcut(SlothIcons.Kind.BOOKMARK, "Bookmarks", "panel:bookmarks"));
        shortcutGrid.addView(shortcut(SlothIcons.Kind.CLOCK, "History", "panel:history"));
        shortcutGrid.addView(shortcut(SlothIcons.Kind.DOWNLOAD, "Downloads", "panel:downloads"));
        shortcutGrid.addView(shortcut(SlothIcons.Kind.SEARCH, "Find", "find"));
        shortcutGrid.addView(shortcut(SlothIcons.Kind.MENU, "Settings", "settings"));
        return shortcutGrid;
    }

    private View shortcut(SlothIcons.Kind kind, String name, final String target) {
        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(dp(2), dp(10), dp(2), dp(8));
        cell.setTag("shortcut");
        cell.setContentDescription(name);
        cell.setClickable(true);
        cell.setFocusable(true);
        cell.setMinimumHeight(dp(76));
        cell.setBackground(tile(16));
        cell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tap(target);
                picked();
            }
        });

        FrameLayout disc = new FrameLayout(activity);
        disc.setBackground(fill(12, SlothTheme.lineAt(0x1A), 0));
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(20), dp(20));
        iconLp.gravity = Gravity.CENTER;
        disc.addView(SlothIcons.decorative(activity, kind, 20, SlothTheme.ink()), iconLp);
        cell.addView(disc, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = text(name, 12f, SlothTheme.ink(), false);
        title.setGravity(Gravity.CENTER);
        // maxLines, not singleLine: a single-line field with an ellipsis drew no text at all in
        // the render, which is how the old shortcut row shipped with icons and no words
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams titleLp = full();
        titleLp.topMargin = dp(6);
        cell.addView(title, titleLp);
        return cell;
    }

    /**
     * Dim a direction the browser has run out of history for. The app keeps its own back and
     * forward buttons and knows when they are dead, so it is asked rather than guessed at.
     */
    void syncHistory(View appBack, View appForward) {
        dimIfDead(backCell, appBack, "Back", "Back, no earlier page");
        dimIfDead(forwardCell, appForward, "Forward", "Forward, no later page");
    }

    private static void dimIfDead(View cell, View appButton, String live, String dead) {
        if (cell == null) {
            return;
        }
        boolean off = appButton != null && !appButton.isEnabled();
        cell.setAlpha(off ? 0.38f : 1f);
        cell.setContentDescription(off ? dead : live);
    }

    /* ---------------------------------------------------------------- widgets */
    /** The widgets fold away: tabs and places come first, and the shelf waits to be asked. */
    private View widgetHeader() {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(14), 0, dp(6));

        LinearLayout toggle = new LinearLayout(activity);
        toggle.setTag("widgets-toggle");
        toggle.setOrientation(LinearLayout.HORIZONTAL);
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setMinimumHeight(dp(48));
        toggle.setClickable(true);
        toggle.setFocusable(true);
        toggle.addView(text("Widgets", 15f, SlothTheme.ink(), true));
        widgetChevron = SlothIcons.decorative(activity, SlothIcons.Kind.FORWARD, 14,
                SlothTheme.dim());
        LinearLayout.LayoutParams chevronLp = new LinearLayout.LayoutParams(dp(14), dp(14));
        chevronLp.leftMargin = dp(8);
        toggle.addView(widgetChevron, chevronLp);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setWidgetsOpen(!widgetsOpen);
            }
        });
        header.addView(toggle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        editButton = text("Edit", 13f, SlothTheme.accent(), true);
        editButton.setTag("edit-widgets");
        editButton.setGravity(Gravity.CENTER);
        editButton.setPadding(dp(16), 0, dp(16), 0);
        editButton.setMinHeight(dp(48));
        editButton.setMinWidth(dp(48));
        editButton.setBackground(press(999));
        editButton.setContentDescription("Edit widgets");
        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onEditWidgets != null) {
                    onEditWidgets.run();
                }
            }
        });
        header.addView(editButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        widgetHeader = toggle;
        return header;
    }

    boolean widgetsOpen() {
        return widgetsOpen;
    }

    void setWidgetsOpen(boolean open) {
        widgetsOpen = open;
        try {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean("widgetsOpen", open).apply();
        } catch (Throwable ignored) {
            SlothLog.swallow("SlothBoard.setWidgetsOpen", ignored);
            // the shelf still opens; it just will not remember
        }
        applyWidgetsOpen();
        if (onResize != null) {
            onResize.run();
        }
    }

    private void applyWidgetsOpen() {
        if (widgetBody != null) {
            widgetBody.setVisibility(widgetsOpen ? View.VISIBLE : View.GONE);
        }
        if (editButton != null) {
            editButton.setVisibility(widgetsOpen ? View.VISIBLE : View.GONE);
        }
        if (widgetChevron != null) {
            widgetChevron.setRotation(widgetsOpen ? 90f : 0f);
        }
        if (widgetHeader != null) {
            widgetHeader.setContentDescription(widgetsOpen
                    ? "Widgets, shown. Tap to fold away"
                    : "Widgets, folded away. Tap to show");
        }
    }

    private View widgets() {
        widgetBody = new LinearLayout(activity);
        widgetBody.setOrientation(LinearLayout.VERTICAL);
        widgetBody.setTag("widget-grid");

        SlothGrid first = new SlothGrid(activity, 2, dp(10));
        first.addView(widget("clock", card("Clock", clockBody(), false)));
        first.addView(widget("calc", calculatorCard()));
        widgetBody.addView(first, spaced());

        View pad = calculatorPad();
        own("calc", pad);
        widgetBody.addView(pad);
        View notes = widget("notes", card("Notes", notesBody(), false));
        widgetBody.addView(notes, spaced());

        SlothGrid counters = new SlothGrid(activity, 2, dp(10));
        counters.addView(widget("tabs", card("Tabs", counterBody(true), false)));
        counters.addView(widget("note", card("Note", counterBody(false), false)));
        widgetBody.addView(counters, spaced());
        applyWidgetVisibility();
        return widgetBody;
    }

    private LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams lp = full();
        lp.bottomMargin = dp(10);
        return lp;
    }

    private View widget(String kind, View tile) {
        widgetTiles.put(kind, tile);
        own(kind, tile);
        return tile;
    }

    private void own(String kind, View view) {
        java.util.ArrayList<View> views = widgetViews.get(kind);
        if (views == null) {
            views = new java.util.ArrayList<>();
            widgetViews.put(kind, views);
        }
        if (!views.contains(view)) {
            views.add(view);
        }
    }

    /** A widget the chooser switched off is gone, and everything it owns goes with it. */
    void applyWidgetVisibility() {
        for (java.util.Map.Entry<String, java.util.ArrayList<View>> entry : widgetViews.entrySet()) {
            boolean hidden = hiddenWidgets.contains(entry.getKey());
            for (View view : entry.getValue()) {
                if (hidden) {
                    view.setVisibility(View.GONE);
                    hiddenByUs.add(view);
                } else if (view == widgetTiles.get(entry.getKey()) || hiddenByUs.remove(view)) {
                    // the tile always comes back; a keypad only if this class hid it
                    view.setVisibility(View.VISIBLE);
                }
            }
        }
    }

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

    void toggleWidget(String kind) {
        if (!hiddenWidgets.remove(kind)) {
            hiddenWidgets.add(kind);
        }
        try {
            // committed, not applied: a choice lost to a process kill is one made twice
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putStringSet("hiddenWidgets", new java.util.LinkedHashSet<>(hiddenWidgets))
                    .commit();
        } catch (Throwable ignored) {
            SlothLog.swallow("SlothBoard.toggleWidget", ignored);
            // shown or hidden for this session at least
        }
        applyWidgetVisibility();
    }

    /** A tile: a small capital label over its body. Only a tile that does something is clickable. */
    private LinearLayout card(String name, View body, boolean clickable) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(14), dp(12), dp(14), dp(12));
        tile.setBackground(fill(SlothTheme.radius(), SlothTheme.card(), SlothTheme.hairline(0x33)));
        tile.setContentDescription(name);
        tile.setClickable(clickable);
        tile.setFocusable(clickable);
        TextView label = text(name.toUpperCase(Locale.UK), 11f, SlothTheme.dim(), true);
        label.setLetterSpacing(0.12f);
        tile.addView(label);
        tile.addView(body, full());
        return tile;
    }

    private View clockBody() {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        clock = text(now("HH:mm"), 28f, SlothTheme.ink(), true);
        body.addView(clock);
        body.addView(text(now("EEEE d MMMM"), 12f, SlothTheme.dim(), false));
        SlothShell.every(30000, new Runnable() {
            @Override
            public void run() {
                if (clock != null) {
                    clock.setText(now("HH:mm"));
                }
            }
        });
        return body;
    }

    private static String now(String pattern) {
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date());
    }

    private View counterBody(boolean tabs) {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        TextView big = text(tabs ? "0 tabs" : "empty", 16f, SlothTheme.ink(), true);
        if (tabs) {
            tabsStat = big;
        } else {
            noteStat = big;
        }
        body.addView(big);
        body.addView(text(tabs ? "open" : "on this phone", 11f, SlothTheme.dim(), false));
        return body;
    }

    private View notesBody() {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        final EditText note = new EditText(activity);
        note.setBackground(null);
        note.setTextColor(SlothTheme.ink());
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        note.setGravity(Gravity.TOP);
        note.setPadding(0, dp(6), 0, 0);
        note.setMinLines(2);
        note.setMinHeight(dp(64));
        note.setHintTextColor(SlothTheme.dim());
        note.setHint("Write something. It stays on the phone.");
        note.setContentDescription("Notes");
        // an EditText asks its parents not to intercept; withdrawn on the down so a drag that
        // starts on the note still moves the drawer, and a tap still edits
        note.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                    for (android.view.ViewParent p = v.getParent(); p != null; p = p.getParent()) {
                        p.requestDisallowInterceptTouchEvent(false);
                    }
                }
                return false;
            }
        });
        try {
            note.setText(activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString("note", ""));
        } catch (Throwable ignored) {
            SlothLog.swallow("SlothBoard.onTouch", ignored);
            // an empty note
        }
        note.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
                try {
                    activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit().putString("note", s.toString()).apply();
                } catch (Throwable ignored) {
                    SlothLog.swallow("SlothBoard.onTextChanged", ignored);
                    // the text stays on screen
                }
                if (noteStat != null) {
                    noteStat.setText(s.length() == 0 ? "empty" : s.length() + " chars");
                }
            }

            public void afterTextChanged(android.text.Editable s) {
            }
        });
        body.addView(note, full());

        LinearLayout meta = new LinearLayout(activity);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.addView(text("Saved on this phone", 11.5f, SlothTheme.dim(), false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView clear = text("Clear", 13f, SlothTheme.accent(), true);
        clear.setContentDescription("Clear the note");
        clear.setGravity(Gravity.CENTER);
        clear.setPadding(dp(14), 0, dp(14), 0);
        clear.setMinHeight(dp(48));
        clear.setMinWidth(dp(48));
        clear.setBackground(press(999));
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                note.setText("");
            }
        });
        meta.addView(clear);
        body.addView(meta);
        if (noteStat != null) {
            noteStat.setText(note.length() == 0 ? "empty" : note.length() + " chars");
        }
        return body;
    }

    void onTabCount(int count) {
        if (tabsStat != null) {
            tabsStat.setText(count == 1 ? "1 tab" : count + " tabs");
        }
        if (tabCountBadge != null) {
            tabCountBadge.setText(String.valueOf(count));
            tabCountBadge.setContentDescription(count == 1 ? "1 tab open" : count + " tabs open");
        }
    }

    /* ------------------------------------------------------------- calculator */
    private double calcValue;
    private double calcStored;
    private String calcOp = "";
    private boolean calcFresh = true;
    private TextView calcFace;
    private LinearLayout calcPad;

    private View calculatorCard() {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        calcFace = text("0", 24f, SlothTheme.ink(), true);
        calcFace.setSingleLine(true);
        calcFace.setEllipsize(TextUtils.TruncateAt.START);
        body.addView(calcFace);
        body.addView(text("Tap for keys", 12f, SlothTheme.dim(), false));
        LinearLayout tile = card("Calc", body, true);
        tile.setContentDescription("Calculator. Tap to show the keys");
        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (calcPad != null) {
                    calcPad.setVisibility(calcPad.getVisibility() == View.VISIBLE
                            ? View.GONE : View.VISIBLE);
                }
            }
        });
        return tile;
    }

    private View calculatorPad() {
        calcPad = new LinearLayout(activity);
        calcPad.setTag("calc-pad");
        calcPad.setOrientation(LinearLayout.VERTICAL);
        calcPad.setVisibility(View.GONE);
        LinearLayout.LayoutParams lp = full();
        lp.bottomMargin = dp(10);
        calcPad.setLayoutParams(lp);
        String[] keys = {"C", "÷", "×", "−", "7", "8", "9", "+",
                "4", "5", "6", "=", "1", "2", "3", ".", "0"};
        SlothGrid grid = new SlothGrid(activity, 4, dp(6));
        for (final String key : keys) {
            TextView k = text(key, 17f, SlothTheme.ink(), true);
            k.setGravity(Gravity.CENTER);
            k.setBackground(fill(12, SlothTheme.card(), SlothTheme.hairline(0x33)));
            k.setContentDescription("Calculator " + key);
            k.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
            k.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    calcKey(key);
                }
            });
            grid.addView(k);
        }
        calcPad.addView(grid, full());
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
        if ("+".equals(key) || "−".equals(key) || "×".equals(key) || "÷".equals(key)) {
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

    private static double apply(double a, double b, String op) {
        if ("+".equals(op)) {
            return a + b;
        }
        if ("−".equals(op)) {
            return a - b;
        }
        if ("×".equals(op)) {
            return a * b;
        }
        if ("÷".equals(op)) {
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
        return String.valueOf(Math.round(value * 10000d) / 10000d);
    }

    /* ------------------------------------------------------------------- tabs */
    /** The tag a card's readable title is kept under, for the filter and the staleness check. */
    static final int R_TITLE = 0x7F5100A1;

    /** Two columns of tab cards only where a title still gets room: wide enough, normal text. */
    static boolean roomyTabs(android.content.Context context) {
        android.util.DisplayMetrics m = context.getResources().getDisplayMetrics();
        float widthDp = m.widthPixels / Math.max(0.1f, m.density);
        float textScale = context.getResources().getConfiguration().fontScale;
        return widthDp >= 400 && textScale <= 1.3f;
    }

    /**
     * Bring the cards in line with the browser's own tab strip. An unchanged strip keeps
     * its cards, so the 800 ms tick does not rebuild listeners and drawables for nothing.
     */
    void mirror(Activity activity) {
        ViewGroup strip = SlothShell.resolveStrip(activity);
        if (strip == null) {
            return;
        }
        if (tabGrid.getChildCount() == strip.getChildCount() && strip.getChildCount() > 0
                && !stale(strip)) {
            return;
        }
        tabFilter.setVisibility(strip.getChildCount() > FILTER_AT ? View.VISIBLE : View.GONE);
        tabGrid.removeAllViews();
        int active = activeIndex(strip);
        for (int i = 0; i < strip.getChildCount(); i++) {
            tabGrid.addView(tabCard(strip.getChildAt(i),
                    readableTitle(SlothShell.label(strip.getChildAt(i)), i + 1), i == active));
        }
        applyFilter(tabFilter.getText().toString());
    }

    void mirrorNow() {
        tabGrid.removeAllViews();
        mirror(activity);
    }

    static int activeIndex(ViewGroup strip) {
        for (int i = 0; i < strip.getChildCount(); i++) {
            View chip = strip.getChildAt(i);
            if (chip.isSelected() || chip.isActivated()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * One tab: its initial on a tinted disc, its title over two lines, and its own close.
     * The tab you are on carries the accent edge and says "Current tab" in words.
     */
    private View tabCard(final View chip, final CharSequence title, boolean current) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(8), dp(4), dp(8));
        card.setMinimumHeight(dp(64));
        GradientDrawable bg = fill(SlothTheme.radius(),
                current ? SlothTheme.lineAt(0x22) : SlothTheme.card(),
                current ? SlothTheme.accent() : SlothTheme.hairline(0x33));
        if (current) {
            bg.setStroke(dp(2), SlothTheme.accent());
        }
        card.setBackground(bg);
        card.setTag("tab-card");
        card.setTag(R_TITLE, title.toString());
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(current ? title + ", current tab" : "Switch to " + title);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chip.performClick();
                picked();
            }
        });

        String trimmed = title.toString().trim();
        TextView initial = text(trimmed.isEmpty() ? "?" : trimmed.substring(0, 1)
                .toUpperCase(Locale.UK), 15f, current ? SlothTheme.accentInk() : SlothTheme.ink(), true);
        initial.setGravity(Gravity.CENTER);
        initial.setBackground(fill(12, current ? SlothTheme.accent() : SlothTheme.lineAt(0x1A), 0));
        initial.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(initial, new LinearLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout words = new LinearLayout(activity);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(10), 0, dp(2), 0);
        TextView name = text(trimmed, 13f, SlothTheme.ink(), true);
        // one line, cut with an ellipsis: two lines split a long word in the middle
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        words.addView(name);
        if (current) {
            TextView here = text("Current tab", 11f, SlothTheme.accent(), true);
            here.setMaxLines(1);
            here.setEllipsize(TextUtils.TruncateAt.END);
            here.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            words.addView(here);
        }
        card.addView(words, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final View closeTarget = SlothShell.byTagDeep(chip, "close");
        View close = roundButton(SlothIcons.Kind.CLOSE, "Close " + title, 12, 0);
        close.setTag("tab-close");
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (closeTarget != null) {
                    closeTarget.performClick();
                } else {
                    SlothLog.warn("this browser offers no close on a tab");
                }
                mirrorNow();
            }
        });
        card.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return card;
    }

    /**
     * The browser names an untitled tab with its spoken description - "Tabs, 1". A card says
     * "Tab 1" instead, and a loaded page keeps its own title.
     */
    static CharSequence readableTitle(CharSequence raw, int position) {
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

    private boolean stale(ViewGroup strip) {
        if (strip.getChildCount() != tabGrid.getChildCount()) {
            return true;
        }
        int active = activeIndex(strip);
        for (int i = 0; i < strip.getChildCount(); i++) {
            View card = tabGrid.getChildAt(i);
            CharSequence want = readableTitle(SlothShell.label(strip.getChildAt(i)), i + 1);
            if (!want.toString().equals(card.getTag(R_TITLE))) {
                return true;
            }
            boolean markedCurrent = String.valueOf(card.getContentDescription()).endsWith(", current tab");
            if (markedCurrent != (i == active)) {
                return true;
            }
        }
        return false;
    }

    /* --------------------------------------------------------------- entrance */
    /**
     * Bring the sections in one after another: a sheet that arrives in a short sequence reads
     * as something that opened. A close or a rebuild cancels it and leaves everything in.
     */
    void reveal() {
        final int count = root.getChildCount();
        if (count == 0) {
            return;
        }
        final int rise = dp(10);
        for (int i = 0; i < count; i++) {
            root.getChildAt(i).setAlpha(0f);
            root.getChildAt(i).setTranslationY(rise);
        }
        final int[] frame = {0};
        final int frames = count + 7;
        final int started = SlothShell.generation();
        SlothShell.post(new Runnable() {
            @Override
            public void run() {
                if (SlothShell.generation() != started) {
                    settleNow();
                    return;
                }
                frame[0]++;
                for (int i = 0; i < count && i < root.getChildCount(); i++) {
                    float t = Math.max(0f, Math.min(1f, (frame[0] - i) / 7f));
                    root.getChildAt(i).setAlpha(t);
                    root.getChildAt(i).setTranslationY(rise * (1f - t));
                }
                if (frame[0] < frames) {
                    SlothShell.postDelayed(this, 16);
                } else {
                    settleNow();
                }
            }
        });
    }

    void settleNow() {
        for (int i = 0; i < root.getChildCount(); i++) {
            root.getChildAt(i).setAlpha(1f);
            root.getChildAt(i).setTranslationY(0f);
        }
    }

    /* ------------------------------------------------------------------ utils */
    private void picked() {
        if (onPick != null) {
            onPick.run();
        }
    }

    private void closeDrawer() {
        picked();
    }

    private void tap(String target) {
        if ("find".equals(target)) {
            SlothShell.openFind(activity);
            return;
        }
        if ("settings".equals(target)) {
            SlothShell.openSettingsPage(activity);
            return;
        }
        if (target.startsWith("panel:")) {
            String kind = target.substring(6);
            if ("bookmarks".equals(kind) || "history".equals(kind) || "downloads".equals(kind)) {
                // records the app or the engine hands over; our page lists them itself
                SlothShell.openLibrary(activity, kind);
            } else {
                SlothShell.openPanel(activity, kind);
            }
            return;
        }
        View v = SlothShell.resolve(activity, target);
        if (v != null) {
            v.performClick();
        } else {
            SlothLog.warn("no " + target + " in this browser");
        }
    }

    /** A round control with a drawn icon: a 44 dp target, whatever the icon's size. */
    private View roundButton(SlothIcons.Kind kind, String label, int iconDp, int fillColour) {
        FrameLayout button = new FrameLayout(activity);
        button.setContentDescription(label);
        button.setClickable(true);
        button.setFocusable(true);
        button.setBackground(fillColour == 0 ? press(999) : fill(999, fillColour, 0));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(iconDp), dp(iconDp));
        lp.gravity = Gravity.CENTER;
        button.addView(SlothIcons.decorative(activity, kind, iconDp, SlothTheme.dim()), lp);
        return button;
    }

    private TextView text(String value, float size, int colour, boolean bold) {
        TextView t = new TextView(activity);
        t.setText(value);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTextColor(colour);
        t.setIncludeFontPadding(false);
        if (bold) {
            t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return t;
    }

    private GradientDrawable fill(float radiusDp, int colour, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(radiusDp * activity.getResources().getDisplayMetrics().density);
        d.setColor(colour);
        if (stroke != 0) {
            d.setStroke(Math.max(1, dp(1)), stroke);
        }
        return d;
    }

    /**
     * A tile you can press: the card colour at rest, so the painted scene behind the sheet never
     * shows through a control, and a lifted fill under the finger.
     */
    private android.graphics.drawable.Drawable tile(int radiusDp) {
        android.graphics.drawable.StateListDrawable states =
                new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},
                fill(radiusDp, SlothTheme.cardTop(), SlothTheme.hairline(0x55)));
        states.addState(new int[]{}, fill(radiusDp, SlothTheme.card(), SlothTheme.hairline(0x33)));
        return states;
    }

    /** A press state without a drawable resource: clear at rest, a soft fill under the finger. */
    private android.graphics.drawable.Drawable press(int radiusDp) {
        android.graphics.drawable.StateListDrawable states =
                new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},
                fill(radiusDp, SlothTheme.lineAt(0x22), 0));
        states.addState(new int[]{}, fill(radiusDp, 0, 0));
        return states;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
