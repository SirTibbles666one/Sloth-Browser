package com.sloth.browser;

/**
 * Says which surface claimed a drag, then gets out of the way.
 *
 * <p>Every surface of the shell can start the same gesture - the bar, the tab strip, the content,
 * the bottom band, a card. When the drag feels wrong, the first question is which one took it, and
 * until now the trace could not answer that: it recorded where the sheet went, never who moved it.</p>
 */
final class SlothLabel implements SlothBar.Listener {

    private final SlothBar.Listener inner;
    private final String name;

    SlothLabel(SlothBar.Listener inner, String name) {
        this.inner = inner;
        this.name = name;
    }

    private void mark() {
        SlothShell.dragSource = name;
    }

    @Override
    public void onDragStart(float y, float carry) {
        mark();
        inner.onDragStart(y, carry);
    }

    @Override
    public void onDrag(float dy) {
        inner.onDrag(dy);
    }

    @Override
    public void onRelease(float velocity) {
        inner.onRelease(velocity);
    }

    @Override
    public void onTap() {
        inner.onTap();
    }

    @Override
    public boolean onSwipe(float dx) {
        return inner.onSwipe(dx);
    }
}
