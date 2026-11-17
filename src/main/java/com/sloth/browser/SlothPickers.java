package com.sloth.browser;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The two lists that open over the drawer: which widgets it holds, and which palette it
 * wears. They are covers, not dialogs - each fills the drawer, has a title and a Close, and
 * a row per choice. What a choice does to the shell is the host's business.
 */
final class SlothPickers {

    interface Host {
        /** Whether this widget is on the drawer now. */
        boolean widgetShown(String kind);

        /** Put the widget on the drawer or take it off. The list stays open. */
        void toggleWidget(String kind);

        /** Wear this palette. The list closes. */
        void pickPalette(int which);
    }

    private SlothPickers() {
    }

    /** Add widget: the things the drawer can hold, with the ones on it ticked. */
    static FrameLayout widgets(Activity activity, final Host host) {
        final FrameLayout cover = cover(activity, "widgets");
        LinearLayout column = column(activity, cover);
        column.addView(head(activity, "Widgets", "Close the widget list", cover));
        for (final String kind : SlothBoard.KINDS) {
            boolean shown = host.widgetShown(kind);
            LinearLayout row = row(activity, "widget-row", SlothBoard.kindLabel(kind), shown, 48);
            // a drawn mark, like every other control: a text tick and plus arrive in the
            // font's weight and sit on its baseline, not in the middle of the row
            // the row is the control and says what it is; the mark only shows the state,
            // and a screen reader hears that as the row being selected
            row.setSelected(shown);
            row.addView(SlothIcons.decorative(activity,
                    shown ? SlothIcons.Kind.CHECK : SlothIcons.Kind.PLUS, 18, SlothTheme.accent()));
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    host.toggleWidget(kind);
                }
            });
            column.addView(row);
        }
        return cover;
    }

    /** Every palette, with its own colours on show, so the choice is not a word. */
    static FrameLayout palette(Activity activity, final Host host) {
        final FrameLayout cover = cover(activity, "palette");
        LinearLayout column = column(activity, cover);
        column.addView(head(activity, "Palette", "Close the palette list", cover));

        ScrollView scroller = new ScrollView(activity);
        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, dp(activity, 6), 0, 0);
        for (int i = 0; i < SlothTheme.NAMES.length; i++) {
            rows.addView(paletteRow(activity, host, i));
        }
        scroller.addView(rows);
        column.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return cover;
    }

    private static View paletteRow(Activity activity, final Host host, final int which) {
        boolean current = which == SlothTheme.index();
        LinearLayout row = row(activity, "palette-row", SlothTheme.FULL[which], current, 52);
        row.setSelected(current);
        if (current) {
            // a tick, not just a tinted outline: the selected row has to be obvious
            SlothIcons tick = SlothIcons.of(activity, SlothIcons.Kind.CHECK, 18, SlothTheme.accent());
            tick.setContentDescription("Current palette");
            // the icon's own fixed size: a wrap would let a drawn mark take the whole row
            LinearLayout.LayoutParams tickLp = (LinearLayout.LayoutParams) tick.getLayoutParams();
            tickLp.rightMargin = dp(activity, 8);
            row.addView(tick, tickLp);
        }
        int[] swatches = {SlothTheme.bg(which), SlothTheme.card(which), SlothTheme.accent(which)};
        for (int colour : swatches) {
            View dot = new View(activity);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(colour);
            shape.setStroke(Math.max(1, dp(activity, 1)), SlothTheme.lineAt(0x33));
            dot.setBackground(shape);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                    dp(activity, 16), dp(activity, 16));
            dotLp.leftMargin = dp(activity, 6);
            row.addView(dot, dotLp);
        }
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.pickPalette(which);
            }
        });
        return row;
    }

    /* ------------------------------------------------------------------ parts */

    private static FrameLayout cover(Activity activity, String tag) {
        FrameLayout cover = new FrameLayout(activity);
        cover.setTag(tag);
        cover.setBackgroundColor(SlothTheme.surface());
        cover.setVisibility(View.GONE);
        cover.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return cover;
    }

    private static LinearLayout column(Activity activity, FrameLayout cover) {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(activity, 16), dp(activity, 10), dp(activity, 16), dp(activity, 10));
        cover.addView(column);
        return column;
    }

    /** The title on the left and Close on the right, the same on both lists. */
    private static View head(Activity activity, String title, String closeSays, final View cover) {
        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(activity, title, 13f, SlothTheme.muted(), true);
        name.setLetterSpacing(0.08f);
        head.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = text(activity, "Close", 13f, SlothTheme.ink(), false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription(closeSays);
        close.setBackground(SlothShell.box(activity, 999, 0, SlothTheme.lineAt(0x33)));
        close.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cover.setVisibility(View.GONE);
            }
        });
        head.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 36)));
        return head;
    }

    /** A card row: its label, room for marks at the end, outlined brighter when chosen. */
    private static LinearLayout row(Activity activity, String tag, String label, boolean chosen,
            int heightDp) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
        row.setTag(tag);
        row.setContentDescription(label);
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(SlothShell.box(activity, 14, SlothTheme.card(),
                chosen ? SlothTheme.lineAt(0x8C) : SlothTheme.lineAt(0x1A)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, heightDp));
        lp.bottomMargin = dp(activity, 8);
        row.setLayoutParams(lp);
        row.addView(text(activity, label, 14f, SlothTheme.ink(), false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private static TextView text(Activity activity, String value, float size, int colour,
            boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setTextColor(colour);
        if (bold) {
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return view;
    }

    private static int dp(Activity activity, float v) {
        return Math.round(v * activity.getResources().getDisplayMetrics().density);
    }
}
