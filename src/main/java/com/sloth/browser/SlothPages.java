package com.sloth.browser;

import java.util.ArrayList;

/**
 * The layers that are open over the page, in the order they were opened.
 *
 * <p>Back closes the most recent one. Before this, Back closed layers in a fixed order -
 * Diagnostics, then Find, then the library, then Settings, then the pickers - so a layer opened
 * on top of another could outlive the one beneath it: Find opened over Diagnostics, and Back
 * took Diagnostics away from under it.</p>
 *
 * <p>A layer can close without Back - its own Close button, a pick, the drawer shutting under
 * a list - so each layer says for itself whether it is still showing, and the stack forgets the
 * ones that are not before it answers. Opening a layer that is already on the stack moves it to
 * the top.</p>
 */
final class SlothPages {

    /** One layer: whether it is on screen now, and how to take it away. */
    interface Layer {
        String name();

        boolean showing();

        void close();
    }

    private static final ArrayList<Layer> STACK = new ArrayList<>();

    private SlothPages() {
    }

    /** A layer was just opened: it is now the one Back closes first. */
    static void opened(Layer layer) {
        if (layer == null) {
            return;
        }
        for (int i = STACK.size() - 1; i >= 0; i--) {
            if (STACK.get(i).name().equals(layer.name())) {
                STACK.remove(i);
            }
        }
        STACK.add(layer);
    }

    /** The most recent layer still showing, or null. Closed ones are forgotten on the way. */
    static Layer top() {
        while (!STACK.isEmpty()) {
            Layer last = STACK.get(STACK.size() - 1);
            boolean showing;
            try {
                showing = last.showing();
            } catch (Throwable gone) {
                SlothLog.swallow("SlothPages.top", gone);
                showing = false;
            }
            if (showing) {
                return last;
            }
            STACK.remove(STACK.size() - 1);
        }
        return null;
    }

    /** Close the most recent layer. False when nothing on the stack is showing. */
    static boolean closeTop() {
        Layer top = top();
        if (top == null) {
            return false;
        }
        STACK.remove(STACK.size() - 1);
        top.close();
        SlothLog.note("back closed " + top.name());
        return true;
    }

    /** The open layers, oldest first, for Diagnostics and the harness. */
    static String describe() {
        top();
        StringBuilder out = new StringBuilder();
        for (Layer layer : STACK) {
            if (out.length() > 0) {
                out.append(" > ");
            }
            out.append(layer.name());
        }
        return out.length() == 0 ? "none" : out.toString();
    }

    /** A new window starts with nothing open. */
    static void clear() {
        STACK.clear();
    }
}
