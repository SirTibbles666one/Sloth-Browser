package com.sloth.browser;

import android.view.View;
import android.view.ViewGroup;

/**
 * Whether the app's welcome screen is on top.
 *
 * <p>That screen is the app's, not the shell's. It is the visible view whose resource
 * name is {@code onboard}, which is the id the app gives it. A test may use the same
 * word as a tag. Callers decide what to do about it; this only answers the question.</p>
 */
final class SlothFirstRun {

    private SlothFirstRun() {
    }

    static boolean showing(View root) {
        return contains(root);
    }

    private static boolean contains(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE) {
            return false;
        }
        if ("onboard".equals(view.getTag())) {
            return true;
        }
        int id = view.getId();
        if (id != View.NO_ID) {
            try {
                if ("onboard".equals(view.getResources().getResourceEntryName(id))) {
                    return true;
                }
            } catch (Throwable ignored) {
                // an id with no entry name is not the welcome screen
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (contains(group.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }
}
