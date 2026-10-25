package com.sloth.browser;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * What the shell can see, written down.
 *
 * <p>The fault reports that matter here arrive as a photograph: a black page, a button that
 * did nothing, a box in the wrong place. From a photograph the cause is a guess. This page
 * answers the questions the guess would ask - is there a page view, how big is it, is
 * anything drawn over it, what did the last navigation do, did the app crash - and puts the
 * answer in a form that can be pasted into a message.</p>
 */
final class SlothDiag {

    private SlothDiag() {
    }

    /** The whole report, as plain text. Also what Copy and Save put somewhere. */
    static String report(Activity activity) {
        StringBuilder out = new StringBuilder();
        out.append("sloth diagnostics\n");
        out.append("version: ").append(version(activity)).append('\n');
        out.append(SlothShell.diagLines(activity)).append('\n');
        out.append(SlothCover.describe(activity)).append('\n');
        out.append("log (").append(SlothLog.count()).append(" lines)\n");
        String log = SlothLog.text();
        out.append(log == null || log.isEmpty() ? "(nothing recorded)" : log).append('\n');
        return out.toString();
    }

    private static String version(Activity activity) {
        try {
            android.content.pm.PackageInfo info = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0);
            String name = info.versionName == null ? "unnamed build" : info.versionName;
            return name + " (" + info.getLongVersionCode() + ")";
        } catch (Throwable t) {
            return "unknown";
        }
    }

    /** The page itself: a report you can read, copy, or keep. */
    static View page(final Activity activity) {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(SlothTheme.bg());
        column.setPadding(dp(activity, 18), dp(activity, 16), dp(activity, 18), dp(activity, 18));
        column.setTag("diag-page");

        TextView title = new TextView(activity);
        title.setText("Diagnostics");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setTextColor(SlothTheme.ink());
        column.addView(title);

        TextView hint = new TextView(activity);
        hint.setText("What the shell can see right now. Copy it into a message if something is wrong.");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        hint.setTextColor(SlothTheme.muted());
        hint.setPadding(0, dp(activity, 4), 0, dp(activity, 12));
        column.addView(hint);

        final TextView body = new TextView(activity);
        body.setText(report(activity));
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        body.setTextColor(SlothTheme.ink());
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        ScrollView scroller = new ScrollView(activity);
        scroller.addView(body);
        column.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(activity, 10), 0, 0);

        buttons.addView(button(activity, "COPY", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copy(activity, body.getText().toString());
                body.setText(report(activity));
            }
        }), weight(activity));
        buttons.addView(button(activity, "SAVE", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String where = SlothLog.save(activity);
                SlothLog.note(where == null ? "could not save the report"
                        : "report saved to " + where);
                body.setText(report(activity));
            }
        }), weight(activity));
        buttons.addView(button(activity, "REFRESH", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SlothCover.probe(activity, "manual check");
                body.setText(report(activity));
            }
        }), weight(activity));
        buttons.addView(button(activity, "RELOAD PAGE", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                View reload = SlothShell.resolve(activity, "btnReload");
                if (reload == null) {
                    SlothLog.error("no reload control to tap");
                } else {
                    reload.performClick();
                    SlothLog.note("reload tapped from diagnostics");
                }
                body.setText(report(activity));
            }
        }), weight(activity));
        column.addView(buttons);

        // the page runs to the bottom of the screen, so the system navigation bar gets its
        // own room - the same fault the drawer had
        column.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets insets) {
                int bottom = insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom;
                if (bottom > 0) {
                    v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottom);
                }
                return insets;
            }
        });
        return column;
    }

    private static void copy(Activity activity, String text) {
        try {
            Object service = activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (service instanceof ClipboardManager) {
                ((ClipboardManager) service).setPrimaryClip(
                        ClipData.newPlainText("sloth diagnostics", text));
                SlothLog.note("diagnostics copied");
            }
        } catch (Throwable t) {
            SlothLog.error("could not copy diagnostics: " + t.getClass().getSimpleName());
        }
    }

    private static View button(Activity activity, String label, View.OnClickListener click) {
        TextView view = new TextView(activity);
        view.setText(label);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        view.setTypeface(view.getTypeface(), Typeface.BOLD);
        view.setTextColor(SlothTheme.ink());
        view.setPadding(0, dp(activity, 12), 0, dp(activity, 12));
        android.graphics.drawable.GradientDrawable background =
                new android.graphics.drawable.GradientDrawable();
        background.setCornerRadius(dp(activity, 12));
        background.setColor(SlothTheme.card());
        background.setStroke(Math.max(1, dp(activity, 1)), SlothTheme.hairline(0x44));
        view.setBackground(background);
        view.setClickable(true);
        view.setFocusable(true);
        view.setOnClickListener(click);
        return view;
    }

    private static LinearLayout.LayoutParams weight(Activity activity) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(activity, 8);
        return lp;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
