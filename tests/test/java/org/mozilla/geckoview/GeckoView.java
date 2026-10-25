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

    public GeckoView(Context context) {
        super(context);
    }
}
