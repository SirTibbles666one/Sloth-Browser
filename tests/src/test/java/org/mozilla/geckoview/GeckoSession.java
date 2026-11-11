package org.mozilla.geckoview;

/** A session the shell can ask for a finder. */
public class GeckoSession {
    public final SessionFinder finder = new SessionFinder();

    public SessionFinder getFinder() {
        return finder;
    }
}
