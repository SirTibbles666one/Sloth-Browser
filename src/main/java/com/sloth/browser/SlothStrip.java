package com.sloth.browser;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * The drawer's first seat: one row of tabs under the bar.
 *
 * <p>Close, one square per open tab with its initial, and a new tab. Tap a square to go to that
 * tab; hold it to close it. A vertical drag anywhere on the row - squares included - moves the
 * sheet, which is why the drag is attached to every child and not just the gaps between them.</p>
 */
final class SlothStrip {

    interface Host {
        void close();

        void pick(View chip);

        void newTab();

        /** Hold on a square: close that tab. False when the browser offers no close. */
        boolean closeTab(View chip);
    }

    private final Activity activity;
    private final Host host;
    private final HorizontalScrollView scroll;
    private final LinearLayout row;
    private View.OnTouchListener drag;
    /** What the row was built from, so the 800 ms tick does not rebuild identical squares. */
    private String signature = "";

    SlothStrip(Activity activity, Host host) {
        this.activity = activity;
        this.host = host;
        row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        scroll = new HorizontalScrollView(activity);
        scroll.setTag("peek-strip");
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.setVisibility(View.GONE);
    }

    View view() {
        return scroll;
    }

    LinearLayout row() {
        return row;
    }

    /** The sheet's drag, for the row and every square in it. */
    void setDrag(View.OnTouchListener listener) {
        drag = listener;
        attach(scroll);
    }

    void forget() {
        signature = "";
    }

    /** Rebuild from the browser's own strip, unless nothing it shows has changed. */
    void fill(ViewGroup chips) {
        String now = signatureOf(chips);
        if (now.equals(signature) && row.getChildCount() > 0) {
            return;
        }
        signature = now;
        row.removeAllViews();

        View close = round("peek-close", SlothIcons.Kind.CLOSE, "Close the drawer",
                SlothTheme.ink(), SlothTheme.card());
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.close();
            }
        });
        row.addView(close);

        if (chips != null) {
            int active = SlothBoard.activeIndex(chips);
            for (int i = 0; i < chips.getChildCount(); i++) {
                final View chip = chips.getChildAt(i);
                CharSequence title = SlothBoard.readableTitle(SlothShell.label(chip), i + 1);
                View square = square(title, i == active);
                square.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        host.pick(chip);
                    }
                });
                square.setLongClickable(true);
                square.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        boolean closed = host.closeTab(chip);
                        if (closed) {
                            signature = "";
                        }
                        return closed;
                    }
                });
                row.addView(square);
            }
        }

        View plus = round("peek-new", SlothIcons.Kind.PLUS, "New tab",
                SlothTheme.accentInk(), SlothTheme.accent());
        plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.newTab();
            }
        });
        row.addView(plus);
        // the squares were just made: the drag goes on them too, not only on the row
        attach(scroll);
    }

    private static String signatureOf(ViewGroup chips) {
        if (chips == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < chips.getChildCount(); i++) {
            View chip = chips.getChildAt(i);
            out.append(SlothShell.label(chip)).append(chip.isSelected() || chip.isActivated() ? "*" : "")
                    .append('|');
        }
        return out.toString();
    }

    private void attach(View view) {
        if (drag == null || view == null) {
            return;
        }
        view.setOnTouchListener(drag);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                attach(group.getChildAt(i));
            }
        }
    }

    private View square(CharSequence title, boolean current) {
        FrameLayout tile = new FrameLayout(activity);
        tile.setTag("peek-tab");
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(current ? SlothTheme.accent() : SlothTheme.card());
        bg.setStroke(Math.max(1, dp(1)), current ? SlothTheme.accent() : SlothTheme.hairline(0x33));
        tile.setBackground(bg);
        TextView initial = new TextView(activity);
        String text = title == null ? "" : title.toString().trim();
        initial.setText(text.isEmpty() ? "?" : text.substring(0, 1).toUpperCase(Locale.UK));
        initial.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        initial.setTextColor(current ? SlothTheme.accentInk() : SlothTheme.ink());
        initial.setTypeface(initial.getTypeface(), android.graphics.Typeface.BOLD);
        initial.setGravity(Gravity.CENTER);
        initial.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(initial, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tile.setContentDescription((current ? title + ", current tab" : "Switch to " + title)
                + ", hold to close");
        tile.setClickable(true);
        tile.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
        lp.rightMargin = dp(8);
        tile.setLayoutParams(lp);
        return tile;
    }

    private View round(String tag, SlothIcons.Kind kind, String label, int ink, int fill) {
        FrameLayout box = new FrameLayout(activity);
        box.setTag(tag);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(fill);
        box.setBackground(bg);
        box.setContentDescription(label);
        box.setClickable(true);
        box.setFocusable(true);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(16), dp(16));
        iconLp.gravity = Gravity.CENTER;
        box.addView(SlothIcons.decorative(activity, kind, 16, ink), iconLp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
        lp.rightMargin = dp(8);
        box.setLayoutParams(lp);
        return box;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
