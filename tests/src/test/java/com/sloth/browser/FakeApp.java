package com.sloth.browser;

import android.app.Application;

import com.sloth.browser.vault.TestVault;

/**
 * An application holding a vault, like SlothApp does.
 *
 * <p>The shell reaches the vault through getApplication().getVault(); without this the library
 * falls back to the engine and then to the panel, so the vault path had no coverage at all.</p>
 */
public class FakeApp extends Application {
    public final TestVault vault = new TestVault();

    public TestVault getVault() {
        return vault;
    }
}
