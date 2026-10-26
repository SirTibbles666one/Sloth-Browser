package com.sloth.browser.vault;

import java.util.ArrayList;
import java.util.List;

/** Test double for CryptoVault: the live lists the browser's own panels read. */
public final class TestVault {
    public final List<Object> bookmarks = new ArrayList<>();
    public final List<Object> history = new ArrayList<>();
    public final List<Object> downloads = new ArrayList<>();
    public int persisted;

    public List<Object> getBookmarks() {
        return bookmarks;
    }

    public List<Object> getHistory() {
        return history;
    }

    public List<Object> getDownloads() {
        return downloads;
    }

    public void persist() {
        persisted++;
    }
}
