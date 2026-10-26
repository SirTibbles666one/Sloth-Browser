package com.sloth.browser.vault;

/** Test double for the app's own history record. */
public final class HistoryEntry {
    private final String url;
    private final String title;
    private final long at;

    public HistoryEntry(String url, String title, long at) {
        this.url = url;
        this.title = title;
        this.at = at;
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public long getAt() {
        return at;
    }
}
