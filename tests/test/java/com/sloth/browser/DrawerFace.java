package com.sloth.browser;

import android.app.Activity;
import android.widget.EditText;

/**
 * Stands in for the browser's own address handler in the harness.
 *
 * <p>{@code go} is private here on purpose, because that is how the real one is declared,
 * and a public-only lookup is what made typing an address do nothing.</p>
 */
public final class DrawerFace {

    /** What the last navigation was asked to open. */
    public static String last = null;

    private DrawerFace() {
    }

    private static void go(Activity activity, EditText field) {
        last = field.getText() == null ? "" : field.getText().toString();
    }
}
