package androidx.activity;

/**
 * Compile-time only: the real class ships inside the app's dex.
 *
 * <p>This file is never packaged. It exists so the shell can extend the activity's real
 * back callback by name; if it were dexed it would shadow the app's own copy and break it,
 * which is why the build copies only the shell's classes into the APK.</p>
 */
public abstract class OnBackPressedCallback {

    public OnBackPressedCallback(boolean enabled) {
    }

    public abstract void handleOnBackPressed();

    public void setEnabled(boolean enabled) {
    }

    public boolean isEnabled() {
        return false;
    }
}
