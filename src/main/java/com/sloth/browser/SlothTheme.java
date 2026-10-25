package com.sloth.browser;

import android.content.Context;

/**
 * Three palettes for one interface: Moss & Sloth, Moonlit Forest, Autumn Canopy.
 * Same layout, same spacing, same type - only the colour changes, which is what makes
 * the three mockups one design rather than three.
 */
public final class SlothTheme {

    /** The concept boards' names, for the picker, where there is room for them. */
    public static final String[] FULL = {
            "Moss & Sloth", "Moonlit Forest", "Autumn Canopy", "Alabaster Grove",
            "Grotto Moss", "Forest Floor", "Twilight Canopy", "Root-Level Moss",
            "Sunburst Canopy", "Ancient Bark"
    };

    /** What the sheet is made of, so the concepts differ in material as well as colour. */
    static int texture() {
        switch (index) {
            case 3:
            case 5:
                return 1; // paper
            case 4:
                return 2; // dappled moss
            case 6:
                return 3; // night sky
            case 7:
            case 9:
                return 4; // bark
            case 8:
                return 5; // rays
            default:
                return 0; // plain glass
        }
    }

    public static final String[] NAMES = {
            "Moss", "Moonlit", "Autumn", "Alabaster", "Grotto",
            "Forest Floor", "Twilight", "Root", "Sunburst", "Bark"
    };

    /** argb values, one row per palette */
    private static final int[][] PALETTES = {
            // Moss & Sloth: deep forest, lime accent
            {0xFF0D1411, 0xFF16201B, 0xFF1B2722, 0xFFEEF7F1, 0xFF8FA598,
                    0xFFC9F27E, 0xFF101A12, 0x33C9F27E, 0xFF0F1A15},
            // Moonlit Forest: near-black navy, cyan accent
            {0xFF0A1118, 0xFF111C26, 0xFF16232F, 0xFFE6F4F7, 0xFF8AA3B4,
                    0xFF4FE0D2, 0xFF06181A, 0x334FE0D2, 0xFF0C1620},
            // Autumn Canopy: burnt brown, rust accent
            {0xFF17100C, 0xFF221710, 0xFF2A1D15, 0xFFF7EEE6, 0xFFBFA793,
                    0xFFF2823E, 0xFF1C1008, 0x33F2823E, 0xFF1B120D},
            // Alabaster Grove: pristine light, pale sage accent
            {0xFFEAF0EA, 0xFFF8FBF8, 0xFFFFFFFF, 0xFF26332B, 0xFF6F7D74,
                    0xFF5E8C74, 0xFFFFFFFF, 0x33708C7C, 0xFFF3F7F3},
            // Grotto Moss: deep emerald
            {0xFF0B2A21, 0xFF143D30, 0xFF1A4A3A, 0xFFE8F6EE, 0xFF8EAC9D,
                    0xFF75D692, 0xFF082019, 0x3375D692, 0xFF103428},
            // Forest Floor: light cream, leaf green
            {0xFFE8E4D0, 0xFFF3F0E2, 0xFFFBF9F0, 0xFF2D382B, 0xFF6D7660,
                    0xFF5C8446, 0xFFFFFFFF, 0x337EA969, 0xFFEFEBD9},
            // Twilight Canopy: night blue
            {0xFF081F2A, 0xFF123B4A, 0xFF174857, 0xFFDEF8FF, 0xFF7C9FAA,
                    0xFF45D7E3, 0xFF04202A, 0x3345D7E3, 0xFF0E3340},
            // Root-Level Moss: earthy olive and brown
            {0xFF2A2615, 0xFF3E3924, 0xFF4A442C, 0xFFF0E9D4, 0xFFB1A67F,
                    0xFF99AB62, 0xFF1F1C0F, 0x3399AB62, 0xFF38331F},
            // Sunburst Canopy: warm gold on dark glass
            {0xFF3A2510, 0xFF2E2113, 0xFF3C2C19, 0xFFFFF2D4, 0xFFD1B17B,
                    0xFFF5B63E, 0xFF2A1A08, 0x33F5B63E, 0xFF33240F},
            // Ancient Bark: rugged dark bark
            {0xFF241C14, 0xFF3B3025, 0xFF483A2D, 0xFFE8DCC6, 0xFFA0937E,
                    0xFF8D9B63, 0xFF1C150F, 0x338D9B63, 0xFF332A20},
    };

    /** How much of the accent to keep, for hairline strokes that must suit any palette. */
    static int lineAt(int alpha) {
        return (alpha << 24) | (accent() & 0x00FFFFFF);
    }

    /**
     * Corner radius per palette. The concepts are not only different colours: Grotto is
     * softer and rounder than Ancient Bark, which is cut close to the grain.
     */
    private static final float[] RADIUS = {20f, 22f, 18f, 14f, 26f, 14f, 22f, 18f, 24f, 10f};

    private static int index;

    private SlothTheme() {
    }

    static void load(Context context) {
        try {
            index = context.getSharedPreferences("slothworks", Context.MODE_PRIVATE)
                    .getInt("theme", 0);
        } catch (Throwable ignored) {
        }
        if (index < 0 || index >= PALETTES.length) {
            index = 0;
        }
    }

    static void select(Context context, int which) {
        index = Math.max(0, Math.min(PALETTES.length - 1, which));
        try {
            context.getSharedPreferences("slothworks", Context.MODE_PRIVATE)
                    .edit().putInt("theme", index).apply();
        } catch (Throwable ignored) {
        }
    }

    static int index() {
        return index;
    }

    static String name() {
        return NAMES[index];
    }

    /** the same accessors, for a palette that is not necessarily the active one */
    static int bg(int which) {
        return PALETTES[which][0];
    }

    static int card(int which) {
        return PALETTES[which][2];
    }

    static int accent(int which) {
        return PALETTES[which][5];
    }

    static int bg() {
        return PALETTES[index][0];
    }

    static int surface() {
        return PALETTES[index][1];
    }

    static int card() {
        // on paper the card and the page are the same white; a breath of the accent keeps
        // them from melting together, which is what the light render showed
        return light() ? mix(PALETTES[index][2], accent(), 0.055f) : PALETTES[index][2];
    }

    static int ink() {
        return PALETTES[index][3];
    }

    static int dim() {
        return PALETTES[index][4];
    }

    static int accent() {
        return PALETTES[index][5];
    }

    static int accentInk() {
        return PALETTES[index][6];
    }

    static int line() {
        return PALETTES[index][7];
    }

    /** True for the palettes the concepts render as glass over the forest. */
    static boolean frosted() {
        return bg() != 0xFFFFFFFF && luminance(bg()) < 0.35;
    }

    /** The sheen at the top of a card: a touch off the card colour, never a new one. */
    static int cardTop() {
        int card = card();
        return frosted() ? lighten(card, 0.10f) : darken(card, 0.04f);
    }

    /** Secondary text: the palette's ink, stepped back towards its background. */
    static int muted() {
        // a light palette has less room between ink and paper, so the same mix that reads
        // on Moss reads as grey wash on Alabaster - the render made that plain
        // the dark renders failed the same way the light one did: metadata mixed a quarter
        // of the way to the panel reads as disabled text, so both directions keep it close
        return mix(ink(), bg(), light() ? 0.16f : 0.17f);
    }

    /** True for a palette whose page is lighter than its ink. */
    static boolean light() {
        return brightness(bg()) > 0.6f;
    }

    /** The accent at an alpha, raised on light palettes so hairlines are actually seen. */
    static int hairline(int alpha) {
        // a hairline at its old alpha disappears on every palette, not only the light ones
        int a = Math.min(255, Math.round(alpha * (light() ? 2.2f : 2.0f)));
        return lineAt(a);
    }

    static float brightness(int colour) {
        float r = ((colour >> 16) & 0xFF) / 255f;
        float g = ((colour >> 8) & 0xFF) / 255f;
        float b = (colour & 0xFF) / 255f;
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    static int mix(int a, int b, float towardsB) {
        float t = Math.max(0f, Math.min(1f, towardsB));
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    static int lighten(int colour, float by) {
        int r = (int) Math.min(255, ((colour >> 16) & 0xFF) + 255 * by);
        int g = (int) Math.min(255, ((colour >> 8) & 0xFF) + 255 * by);
        int b = (int) Math.min(255, (colour & 0xFF) + 255 * by);
        return (colour & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    static int darken(int colour, float by) {
        int r = (int) Math.max(0, ((colour >> 16) & 0xFF) - 255 * by);
        int g = (int) Math.max(0, ((colour >> 8) & 0xFF) - 255 * by);
        int b = (int) Math.max(0, (colour & 0xFF) - 255 * by);
        return (colour & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static double luminance(int colour) {
        double[] c = new double[3];
        int[] shift = {16, 8, 0};
        for (int i = 0; i < 3; i++) {
            double v = ((colour >> shift[i]) & 0xFF) / 255.0;
            c[i] = v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    static float radius() {
        return RADIUS[index];
    }

    static int field() {
        return PALETTES[index][8];
    }
}
