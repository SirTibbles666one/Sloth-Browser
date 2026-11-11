package com.sloth.browser;

import android.content.pm.Signature;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The update check compares every signer, not only the first. A shorter list
 * or a swapped one must not count as the same key.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class SlothUiTest {

    @Test
    public void aMissingOrShorterSignerListIsNotTheSame() {
        Signature ours = new Signature(new byte[]{1, 2, 3});
        Signature other = new Signature(new byte[]{4, 5});
        assertFalse(SlothUi.sameSigners(null, new Signature[]{ours}));
        assertFalse(SlothUi.sameSigners(new Signature[]{ours}, null));
        assertFalse(SlothUi.sameSigners(new Signature[0], new Signature[]{ours}));
        assertFalse(SlothUi.sameSigners(new Signature[]{ours}, new Signature[0]));
        assertFalse(SlothUi.sameSigners(new Signature[]{ours}, new Signature[]{ours, other}));
        assertFalse(SlothUi.sameSigners(new Signature[]{ours, null}, new Signature[]{ours, other}));
    }

    @Test
    public void theWholeSignerListHasToMatch() {
        Signature first = new Signature(new byte[]{1, 2, 3});
        Signature second = new Signature(new byte[]{4, 5, 6});
        assertFalse(SlothUi.sameSigners(
                new Signature[]{first, second},
                new Signature[]{second, first}));
        assertTrue(SlothUi.sameSigners(
                new Signature[]{first, second},
                new Signature[]{new Signature(new byte[]{1, 2, 3}),
                        new Signature(new byte[]{4, 5, 6})}));
    }
}
