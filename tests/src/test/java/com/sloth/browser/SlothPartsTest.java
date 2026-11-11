package com.sloth.browser;

import android.view.View;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The three pieces the next chrome can take as they are: progress, the address,
 * and whether first run is on screen. No shell, no bar.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class SlothPartsTest {

    @Test
    public void theHostIsWhatTheBarShows() {
        assertEquals("example.org",
                SlothAddress.host("https://user@example.org:8443/page?q=1"));
        assertEquals("example.org", SlothAddress.host("example.org/leaf"));
        assertEquals("cats and dogs", SlothAddress.host("cats and dogs"));
    }

    @Test
    public void focusRevealsTheUrlAndAnEditSurvives() {
        String full = "https://example.org/page";
        assertEquals(full, SlothAddress.focused(full, "example.org"));
        assertEquals("cats", SlothAddress.focused(full, "cats"));
        assertEquals("example.org", SlothAddress.blurred(full, full));
        assertEquals("cats", SlothAddress.blurred(full, "cats"));
    }

    @Test
    public void progressIsPerSessionAndForwards() {
        org.mozilla.geckoview.GeckoSession session = new org.mozilla.geckoview.GeckoSession();
        final boolean[] forwarded = {false};
        session.setProgressDelegate(new org.mozilla.geckoview.GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(org.mozilla.geckoview.GeckoSession s, String url) {
                forwarded[0] = true;
            }

            @Override
            public void onPageStop(org.mozilla.geckoview.GeckoSession s, boolean success) {
            }

            @Override
            public void onProgressChange(org.mozilla.geckoview.GeckoSession s, int progress) {
            }

            @Override
            public void onSecurityChange(org.mozilla.geckoview.GeckoSession s, Object information) {
            }

            @Override
            public void onSessionStateChange(org.mozilla.geckoview.GeckoSession s, Object state) {
            }
        });
        SlothLoad.attach(session);
        org.mozilla.geckoview.GeckoSession.ProgressDelegate hooked = session.getProgressDelegate();
        hooked.onPageStart(session, "https://example.com/");
        assertTrue(forwarded[0]);
        assertEquals(8, SlothLoad.shown(session));
        hooked.onProgressChange(session, 60);
        assertEquals(60, SlothLoad.shown(session));

        org.mozilla.geckoview.GeckoSession other = new org.mozilla.geckoview.GeckoSession();
        SlothLoad.attach(other);
        assertEquals("the other tab has not started", -1, SlothLoad.shown(other));
        other.getProgressDelegate().onPageStart(other, "https://other.example/");
        assertEquals(8, SlothLoad.shown(other));
        assertEquals("the first tab is still at its own progress", 60, SlothLoad.shown(session));

        hooked.onPageStop(session, true);
        assertEquals(-1, SlothLoad.shown(session));
        assertEquals(8, SlothLoad.shown(other));
    }

    @Test
    public void firstRunIsTheVisibleOnboardView() {
        android.app.Activity activity = Robolectric.buildActivity(android.app.Activity.class)
                .create().get();
        FrameLayout root = new FrameLayout(activity);
        assertFalse(SlothFirstRun.showing(root));

        View welcome = new View(activity);
        welcome.setTag("onboard");
        root.addView(welcome);
        assertTrue(SlothFirstRun.showing(root));

        welcome.setVisibility(View.GONE);
        assertFalse("a dismissed welcome screen is not in the way", SlothFirstRun.showing(root));
    }
}
