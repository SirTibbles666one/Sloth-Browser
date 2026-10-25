package com.sloth.browser;

import android.app.Activity;

import androidx.activity.OnBackPressedCallback;

/**
 * The phone's back button, answering to the shell's own layers first.
 *
 * <p>Settings and Diagnostics are pages the shell draws over the browser, so the browser
 * knows nothing about them: back went straight past them and left the app. A page you
 * cannot leave with the back button does not feel like a page, it feels like a trap.</p>
 *
 * <p>This is registered on the activity's own back dispatcher, so the browser's own back
 * behaviour is untouched the moment nothing of ours is open.</p>
 */
final class SlothBack extends OnBackPressedCallback {

    private final Activity activity;

    SlothBack(Activity activity) {
        super(true);
        this.activity = activity;
    }

    @Override
    public void handleOnBackPressed() {
        if (SlothShell.backOneLayer(activity)) {
            return;
        }
        // nothing of ours is open: step out of the way and let the browser have the press
        setEnabled(false);
        try {
            activity.onBackPressed();
        } finally {
            setEnabled(true);
        }
    }
}
