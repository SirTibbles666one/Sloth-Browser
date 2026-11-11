package org.mozilla.geckoview;

import android.content.Context;
import android.view.View;

/**
 * Stands in for the engine's page view in the harness.
 *
 * <p>The shell finds the page by the field's type name, so the name is what matters here;
 * the class itself only has to be a view.</p>
 */
public class GeckoView extends View {

    private Object session;

    public GeckoView(Context context) {
        super(context);
    }

    /** The shell asks the page for its session by this name. Null until a test sets one. */
    public Object getSession() {
        return session;
    }

    public void setSession(Object session) {
        this.session = session;
    }
}
