package org.mozilla.geckoview;

/** A session the shell can ask for a finder, and for the page's progress. */
public class GeckoSession {
    public final SessionFinder finder = new SessionFinder();

    /** The engine's own shape: every callback has a default, so a delegate implements what it needs. */
    public interface ProgressDelegate {
        default void onPageStart(GeckoSession session, String url) {
        }

        default void onPageStop(GeckoSession session, boolean success) {
        }

        default void onProgressChange(GeckoSession session, int progress) {
        }
    }

    private ProgressDelegate progressDelegate;
    public int stops;

    public SessionFinder getFinder() {
        return finder;
    }

    public ProgressDelegate getProgressDelegate() {
        return progressDelegate;
    }

    public void setProgressDelegate(ProgressDelegate delegate) {
        progressDelegate = delegate;
    }

    public void stop() {
        stops++;
    }
}
