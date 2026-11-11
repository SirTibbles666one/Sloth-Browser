package com.sloth.browser;

/**
 * What the address field shows, and what it must put back when edited.
 *
 * <p>While a page is up the bar shows the host. The full URL stays with the caller.
 * Focusing a field that still shows that host reveals the full URL, which is what
 * {@code go()} has to receive. An edit is left alone: a focus flicker must not
 * wipe it, and leaving the field while it still holds the full URL puts the host back.</p>
 */
final class SlothAddress {

    private SlothAddress() {
    }

    /** A host, rather than a search phrase. */
    static boolean looksLike(String text) {
        if (text == null) {
            return false;
        }
        String value = text.trim();
        if (value.length() == 0 || value.contains(" ")) {
            return false;
        }
        int host = value.indexOf("://") >= 0 ? value.indexOf("://") + 3 : 0;
        String rest = value.substring(host);
        int slash = rest.indexOf('/');
        String name = slash < 0 ? rest : rest.substring(0, slash);
        return name.startsWith("localhost") || (name.contains(".") && !name.startsWith(".")
                && !name.endsWith("."));
    }

    /**
     * The host, for the resting bar. A search phrase is returned unchanged,
     * because trimming it would be inventing an address.
     */
    static String host(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.length() == 0 || !looksLike(value)) {
            return value;
        }
        int scheme = value.indexOf("://");
        String rest = scheme >= 0 ? value.substring(scheme + 3) : value;
        int cut = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                cut = i;
                break;
            }
        }
        String name = rest.substring(0, cut);
        int at = name.lastIndexOf('@');
        if (at >= 0) {
            name = name.substring(at + 1);
        }
        int colon = name.lastIndexOf(':');
        if (colon > 0 && name.indexOf(':') == colon) {
            name = name.substring(0, colon);
        }
        return name;
    }

    /** The field gained focus. Reveal {@code full} only when it is still showing the host. */
    static String focused(String full, String visible) {
        String committed = full == null ? "" : full;
        String showing = visible == null ? "" : visible;
        if (committed.length() > 0 && host(committed).contentEquals(showing)) {
            return committed;
        }
        return showing;
    }

    /**
     * Whether a connection is one we will call secure. Only a real URL counts:
     * {@code https} and {@code about:} are, {@code http} is not, and a host with the
     * scheme already stripped is not evidence either way.
     */
    static boolean secure(String url) {
        if (url == null) {
            return false;
        }
        String u = url.trim().toLowerCase(java.util.Locale.UK);
        return u.startsWith("https://") || u.startsWith("about:");
    }

    /** The field lost focus. The full URL collapses back to the host; an edit stays. */
    static String blurred(String full, String visible) {
        String committed = full == null ? "" : full;
        String showing = visible == null ? "" : visible;
        if (committed.length() > 0 && committed.contentEquals(showing)) {
            return host(committed);
        }
        return showing;
    }
}
