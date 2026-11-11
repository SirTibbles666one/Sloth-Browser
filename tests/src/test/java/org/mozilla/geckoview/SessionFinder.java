package org.mozilla.geckoview;

/** Records the query and the direction flag, and answers with a fixed result. */
public class SessionFinder {
    public String query;
    public int flags = Integer.MIN_VALUE;
    public int clears;

    public GeckoResult<FinderResult> find(String query, int flags) {
        this.query = query;
        this.flags = flags;
        FinderResult found = new FinderResult();
        if ("zzz".equals(query)) {
            found.total = 0;
            found.current = 0;
        } else {
            found.total = 12;
            found.current = 3;
        }
        return GeckoResult.fromValue(found);
    }

    public void clear() {
        clears++;
    }
}
