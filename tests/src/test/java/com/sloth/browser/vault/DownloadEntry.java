package com.sloth.browser.vault;

/** Test double for the app's own download record. */
public final class DownloadEntry {
    private final String name;
    private final String url;
    private final long at;

    public DownloadEntry(String name, String url, long at) {
        this.name = name;
        this.url = url;
        this.at = at;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public long getAt() {
        return at;
    }
}
