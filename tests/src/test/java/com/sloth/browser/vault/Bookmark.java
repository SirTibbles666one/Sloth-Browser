package com.sloth.browser.vault;

/** Test double for the app's own bookmark record: the same two getters the shell reads. */
public final class Bookmark {
    private final String url;
    private final String title;

    public Bookmark(String url, String title) {
        this.url = url;
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }
}
