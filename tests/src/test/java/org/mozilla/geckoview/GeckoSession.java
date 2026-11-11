package org.mozilla.geckoview;

/** A session the shell can ask for a finder, and for page-load progress. */
public class GeckoSession {
    public final SessionFinder finder = new SessionFinder();

    /** The callbacks the engine actually has. The shell wraps whatever was here. */
    public interface ProgressDelegate {
        void onPageStart(GeckoSession session, String url);

        void onPageStop(GeckoSession session, boolean success);

        void onProgressChange(GeckoSession session, int progress);

        void onSecurityChange(GeckoSession session, Object information);

        void onSessionStateChange(GeckoSession session, Object state);
    }

    private ProgressDelegate progressDelegate;

    public SessionFinder getFinder() {
        return finder;
    }

    public ProgressDelegate getProgressDelegate() {
        return progressDelegate;
    }

    public void setProgressDelegate(ProgressDelegate delegate) {
        this.progressDelegate = delegate;
    }
}
