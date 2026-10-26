package com.sloth.browser;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The customisable shell for Sloth Browser 1091: a permanent top search bar and a
 * pull-down drawer holding tabs and widgets.
 *
 * It is an overlay on the activity's content view, so the GeckoView page keeps its
 * own layout. The app's original chrome row is collapsed and hidden, but its bound
 * widgets stay alive: every control here forwards to them with performClick(), so
 * history, tabs, menu and the updater keep the browser's own behaviour. The address
 * field mirrors the real omnibox and hands typed text to MainActivity.go(String).
 */
public final class SlothShell {

    private static final boolean DEBUG = Boolean.getBoolean("sloth.shell.debug");

    /** A view's own post() queues until it is attached; the animation must not depend on that. */
    private static final android.os.Handler MAIN =
            new android.os.Handler(android.os.Looper.getMainLooper());

    // filled from the active theme when the shell is built
    static int BG = 0xFF0F1512;
    static int SURFACE = 0xFF16201B;
    static int CARD = 0xFF1B2722;
    static int INK = 0xFFEEF7F1;
    static int DIM = 0xFF8FA598;
    static int ACCENT = SlothTheme.accent();
    static int LINE = SlothTheme.lineAt(0x1F);

    /**
     * Draw under the system bars instead of inside them.
     *
     * <p>On Android 15 this is already the platform's default and the old status bar colour
     * call is ignored, which is why the band above our panel was the system's. Asking for it
     * explicitly makes the behaviour the same on every device we support: the page reaches the
     * top of the screen, and the panel, the status strip and the nav bar are all ours to
     * paint. Every position here is measured from laid-out views rather than computed from
     * constants, so removing the decor's inset cannot double-count it.</p>
     */
    static void requestEdgeToEdge(Activity activity) {
        Window window = activity == null ? null : activity.getWindow();
        if (window == null) {
            return;
        }
        if (edgeFellBack) {
            // a window that was reverted stays reverted: rebuild() calls this again, and asking
            // a second time undid the fallback one frame after it was applied
            return;
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                window.setDecorFitsSystemWindows(false);
                window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
                window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    window.setStatusBarContrastEnforced(false);
                    window.setNavigationBarContrastEnforced(false);
                }
            } else {
                View decor = window.getDecorView();
                decor.setSystemUiVisibility(decor.getSystemUiVisibility()
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
                window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
                window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            }
            edgeToEdge = true;
        } catch (RuntimeException e) {
            SlothLog.error("could not ask the window to draw under the system bars");
        }
    }

    static void applyTheme() {
        BG = SlothTheme.bg();
        SURFACE = SlothTheme.surface();
        CARD = SlothTheme.card();
        INK = SlothTheme.ink();
        DIM = SlothTheme.dim();
        ACCENT = SlothTheme.accent();
        LINE = SlothTheme.line();
    }

    private SlothShell() {
    }

    /* ------------------------------------------------------------------ state */
    private static View overlay;
    private static TextView clock;
    private static LinearLayout tabRow;
    private static View drawer;
    private static EditText address;
    private static int peekPx;
    /** The seat the drawer last rested on, so haptics fire on a change and not a repeat. */
    private static int lastSeat = -1;
    private static boolean peekFromContent;
    /** True while the app's own settings screen owns the panel. */
    private static boolean settingsOpen;
    private static View libraryPage;
    private static View findBar;
    private static View dock;
    private static View main;
    private static int dockH = -1, dockTop, dockBottom, dockPadL, dockPadR, mainH;
    private static View pill;
    private static View appRoot;
    private static final java.util.HashMap<String, View> lookups = new java.util.HashMap<>();
/** The app's own bar. Always collapsed: nothing in this shell needs it. */
    private static final String[] CHROME = {
            "chromeDock", "chromeMain", "chromeGrip", "chromeToolRow", "chromeFavRow",
            "chromeMore", "chromeWidgetGrid"
    };
    /** The app's panel, which Settings and the menu are built into. Lent, not kept. */
    private static final String[] PANEL = {"panel", "panelBody"};
    private static final java.util.HashMap<String, int[]> legacyState = new java.util.HashMap<>();
    private static int appRootPadTop = -1;
    /** The page's own bottom padding, kept so the nav bar can be cleared under it. */
    private static int appRootPadBottom = -1;
    /** True once the window was asked to draw under the system bars, for the harness. */
    static boolean edgeToEdge;
    /** Set when the edge-to-edge window was rejected on evidence and decor fitting restored. */
    static boolean edgeFellBack;
    private static boolean edgeChecked;
    /** Height of the system status bar; the shell must not draw over it. */
    private static int statusInset;
    /** Whether the drawer is meant to be open; a stray height without this is debris. */
    private static boolean openIntent;
    /** Expanded height, fitted to the content the first time it is measured. */
    private static int expandedPx;
    /** Height the board measured when expandedPx was cached, to notice content changes. */
    private static int measuredContentH;
    private static ScrollView scroller;
    private static SlothBoard board;
    private static View backGlyph;
    /** Controls that recede as the bar becomes the dashboard. */
    private static final java.util.ArrayList<View> chromeViews = new java.util.ArrayList<>();
    /** 1 when collapsed, →0 as the drawer takes over the surface. */
    private static float chromeFade = 1f;
    /** The drawer footer is pinned, so it must not appear before there is room for it. */
    private static View footRow;
    /** Swatches for every palette; the chip opens this instead of cycling ten times. */
    private static View paletteSheet;
    private static View widgetSheet;
    private static View settingsPage;
    /** The diagnostics report: what the shell can see, for a message about a fault. */
    private static View diagPage;
    /** Which windows already carry the back callback. */
    private static final java.util.WeakHashMap<Activity, Boolean> backInstalled =
            new java.util.WeakHashMap<>();
    /** Windows whose callback already carries the fallback wrapper. */
    private static final java.util.WeakHashMap<Activity, Boolean> callbackInstalled =
            new java.util.WeakHashMap<>();
    private static View statusScrim;
    /** True when the status bar glyphs were asked to be dark, for the harness. */
    static boolean statusIconsLight;
    private static View barView;
    /** The painted scene, kept so it can be told how far out the drawer is. */
    private static SlothBackdrop backdropView;
    /** True while the sheet is showing the strip rather than the board. */
    private static boolean peekShowing;
    /** The shadow and hinge line at the sheet's top edge. */
    /** The address field, which fades with the bar rather than staying lit. */
    private static View addressPillView;
    /** What the strip was last built from, so an unchanged strip is not rebuilt. */
    private static String lastPeekSignature = "";
    /** The peek: close, the open tabs, a new tab - the whole of a half-open sheet. */
    private static HorizontalScrollView peekScroll;
    private static LinearLayout peekRow;
    /** One drag for the strip and everything on it, so it starts wherever the finger lands. */
    private static SlothDrag stripDrag;
    private static View barWrapView;
    private static View fwdGlyph;
    private static TextView clearTag;
    private static TextView TabFace;
    private static float downY;
    private static float startH;
    private static boolean dragging;
    /** The union's own state, so a drag does not re-allocate its backgrounds per frame. */
    private static boolean unionOpen;
    /** Whether the board is showing its compact form, for the same reason. */
    private static boolean boardCompact;
    /** A drag collects moves and applies the latest one once per main-loop turn. */
    private static int dragPending = Integer.MIN_VALUE;
    private static boolean dragPosted;
    /** Counters for the diagnostics line: how much work a drag actually did. */
    private static int dragWrites;
    private static int pageRelayouts;
    /**
     * The last drag, kept as numbers.
     *
     * <p>"It still feels wrong" is the most expensive report this project gets: every one of
     * them cost a build and a guess, because a drag exists only while a finger is on the
     * glass. These are the figures that describe one - where it started, how many moves were
     * applied, where it was released, how fast, and which seat it chose.</p>
     */
    private static int traceFrom = -1;
    private static int traceTo = -1;
    private static int traceMoves;
    private static int tracePeak;
    private static float traceVelocity;
    private static boolean traceFlicked;
    private static long traceMs;

    /**
     * Which shell an animation belongs to.
     *
     * <p>Frame runnables were never cancelled. A close that landed mid-animation was undone
     * by the animation's remaining frames, which put the drawer back over the page; a
     * rebuild mid-animation wrote the old target into the new surface. Each frame now
     * checks that its shell still stands.</p>
     */
    private static int gen;

    /** The shell's animation generation: a frame from an older one belongs to a dead gesture. */
    static int generation() {
        return gen;
    }

    /** True while frames are still being posted, so the tidy-up does not fight them. */
    private static boolean animating;

    /* ---------------------------------------------------------------- helpers */
    private static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    private static GradientDrawable round(Context c, float radius, int color, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(c, radius));
        d.setColor(color);
        if (stroke != 0) {
            d.setStroke(Math.max(1, dp(c, 1)), stroke);
        }
        return d;
    }

    static Object field(Object target, String name) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    static View bound(Activity activity, String name) {
        try {
            Object binding = field(activity, "binding");
            Object v = field(binding, name);
            return v instanceof View ? (View) v : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
/**
     * The tab strip is NOT reliably the "slotTabs" binding: the app tags it
     * "tab-squares" (TabSquares) and its chips "tab". A binding lookup that quietly
     * returns null is how the drawer ended up with no tab list at all.
     * Try the binding, then the tag, then infer the parent of the chip-tagged views.
     */
    static ViewGroup resolveStrip(Activity activity) {
        View binding = resolve(activity, "slotTabs");
        if (binding instanceof ViewGroup && ((ViewGroup) binding).getChildCount() > 0) {
            return (ViewGroup) binding;
        }
        View root = activity.getWindow().getDecorView();
        View tagged = byTagDeep(root, "tab-squares");
        if (tagged instanceof ViewGroup && ((ViewGroup) tagged).getChildCount() > 0) {
            return (ViewGroup) tagged;
        }
        // last resort: whichever container holds the views the app tags "tab"
        java.util.Map<ViewGroup, Integer> counts = new java.util.HashMap<>();
        collectTagParents(root, "tab", counts);
        ViewGroup best = null;
        int bestCount = 0;
        for (java.util.Map.Entry<ViewGroup, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }
        return best;
    }

    private static void collectTagParents(View view, String tag, java.util.Map<ViewGroup, Integer> counts) {
        if ("tab".equals(view.getTag()) && view.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) view.getParent();
            Integer seen = counts.get(parent);
            counts.put(parent, seen == null ? 1 : seen + 1);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectTagParents(group.getChildAt(i), tag, counts);
            }
        }
    }

    /**
     * Call a method the way the app does. Everything worth reaching on MainActivity is
     * PRIVATE - showSettings, showSwitcher, runWidget, showMenu - so getMethod finds
     * nothing and the call dies silently in a catch. Walk the declared methods of the
     * class and its superclasses, open them up, then invoke.
     */
    static Object call(Object target, String name, Class<?>[] types, Object[] args) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                java.lang.reflect.Method method = type.getDeclaredMethod(name, types);
                method.setAccessible(true);
                return method.invoke(target, args);
            } catch (NoSuchMethodException ignored) {
                // keep walking up
            }
        }
        throw new NoSuchMethodException(name);
    }

    static View resolve(Activity activity, String name) {
        View cached = lookups.get(name);
        if (cached != null && cached.getContext() == activity) {
            return cached;
        }
        View found = null;
        try {
            java.lang.reflect.Field made = Class.forName("com.sloth.browser.BarUnion")
                    .getDeclaredField("made");
            made.setAccessible(true);
            Object map = made.get(null);
            if (map instanceof java.util.Map) {
                Object v = ((java.util.Map<?, ?>) map).get(name);
                if (v instanceof View) {
                    found = (View) v;
                }
            }
        } catch (Throwable ignored) {
        }
        if (found == null) {
            found = bound(activity, name);
        }
        if (found == null) {
            View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
            found = byTag(decor, name);
        }
        if (found != null) {
            lookups.put(name, found);
        }
        return found;
    }

    static View byTagDeep(View v, String tag) {
        return byTag(v, tag);
    }

    private static View byTag(View v, String name) {
        if (v == null) {
            return null;
        }
        if (name.equals(String.valueOf(v.getTag()))) {
            return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View hit = byTag(g.getChildAt(i), name);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    private static void tap(Activity activity, String name) {
        View v = resolve(activity, name);
        if (v != null) {
            v.performClick();
        }
    }
/** Run on the main thread: the shell's own handler, so a view's queue cannot swallow it. */
    static void post(Runnable work) {
        MAIN.post(work);
    }

    static void postDelayed(Runnable work, long delayMs) {
        MAIN.postDelayed(work, delayMs);
    }

    /** Repeating work with a lifetime: every loop is registered so a new shell can end it. */
    private static final java.util.ArrayList<Runnable> loops = new java.util.ArrayList<>();

    static void every(final long ms, final Runnable work) {
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!loops.contains(this)) {
                    return; // the shell that started this is gone
                }
                try {
                    work.run();
                } finally {
                    if (loops.contains(this)) {
                        MAIN.postDelayed(this, ms);
                    }
                }
            }
        };
        loops.add(tick);
        MAIN.postDelayed(tick, ms);
    }

    /** Stop every repeating loop. Called when a shell is replaced, so tiles do not outlive it. */
    static void stopLoops() {
        for (Runnable loop : loops) {
            MAIN.removeCallbacks(loop);
        }
        loops.clear();
    }

    /* ------------------------------------------------------------------ entry */
    /** The activity the current shell was built into. */
    private static Activity owner;

    /** Package-private so the harness can prove a dead activity is no longer held. */
    static boolean ownsActivity(Activity activity) {
        return owner == activity;
    }

    /**
     * Let go of the activity, its views and its loops.
     *
     * <p>The shell is a set of statics hung off one activity's window, so the only thing
     * keeping a finished activity alive was our own reference. Released on detach and on
     * takeover, it cannot outlive the window it draws into.</p>
     */
    /** Release the shell as a window teardown would, for the harness. */
    static void releaseShellForTest() {
        releaseShell();
    }

    /** The seat the drawer last rested on, for the harness. */
    static int lastSeatForTest() {
        return lastSeat;
    }

    static void releaseShell() {
        stopLoops();
        lookups.clear();
        chromeViews.clear();
        overlay = null;
        clock = null;
        tabRow = null;
        drawer = null;
        address = null;
        dock = null;
        main = null;
        sheetView = null;
        surfaceView = null;
        board = null;
        scroller = null;
        backGlyph = null;
        fwdGlyph = null;
        footRow = null;
        barView = null;
        barWrapView = null;
        paletteSheet = null;
        widgetSheet = null;
        settingsPage = null;
        diagPage = null;
        clearTag = null;
        TabFace = null;
        pill = null;
        dockH = -1;
        mainH = 0;
        peekPx = 0;
        peekFromContent = false;
        openIntent = false;
        settingsOpen = false;
        libraryPage = null;
        findBar = null;
        // the next window's first drag must be able to tick: a seat remembered from a window
        // that is gone would silence the very first settle of the new one
        lastSeat = -1;
        expandedPx = 0;
        measuredContentH = 0;
        dragging = false;
        unionOpen = false;
        boardCompact = false;
        dragPending = Integer.MIN_VALUE;
        dragPosted = false;
        downY = 0;
        startH = 0;
        chromeFade = 1f;
        // the peek and the painted scene came later; the release has to keep up with them
        peekScroll = null;
        peekRow = null;
        stripDrag = null;
        peekShowing = false;
        lastPeekSignature = "";
        backdropView = null;
        // the page root is the whole browser view tree: holding it after detach keeps a
        // GeckoView alive, and keeping its padding makes the next window inherit the last one's
        // insets - a rotation that changes the nav bar then pads the page wrongly
        appRoot = null;
        appRootPadTop = -1;
        appRootPadBottom = -1;
        // a new window has its own insets, and may judge the edge request again
        statusInset = 0;
        navSeen = -1;
        edgeChecked = false;
        // frames already posted check this before touching the tree
        gen++;
        animating = false;
        lastProbe = 0;
        SlothCover.forget();
        owner = null;
    }

    public static void own(final Activity activity) {
        try {
            SlothTheme.load(activity);
            // what went wrong in the last session is worth reading in this one
            SlothLog.attach(activity);
            applyTheme();
        if (activity != null) {
            // before any inset is read: the window must stop fitting content inside the bars,
            // or every measurement below describes the old window
            requestEdgeToEdge(activity);
        }
            guardOff(activity);
            if (overlay == null) {
                // nothing is attached: any loop still running belongs to a dead shell
                stopLoops();
            }
            if (overlay != null) {
                // a rotation or a relaunch leaves the old static pointing at a dead
                // activity; only the live one may reuse the shell
                if (overlay.getContext() == activity && overlay.getParent() != null) {
                    owner = activity;
                    refresh(activity);
                    return;
                }
                // the previous activity is gone: let go of everything that pointed at it
                releaseShell();
            }
            owner = activity;
            // a new shell means new views: anything still animating belongs to the old ones
            gen++;
            animating = false;
            ViewGroup content = (ViewGroup) activity.findViewById(android.R.id.content);
            if (content == null) {
                return;
            }

            FrameLayout root = new FrameLayout(activity);
            root.setTag("shell-root");
            root.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            root.setClipChildren(false);

            // ------------------------------------------------- one surface, not two
            // The bar and the drawer are a single clipped container. The drawer cannot
            // detach, overlap the bar, or leave a sliver behind, because it is not a
            // sibling of the bar any more: it is the part of the bar that grows.
            FrameLayout surface = new FrameLayout(activity);
            surface.setTag("surface");
            surface.setClipChildren(true);
            surface.setClipToOutline(true);
            // the panel is a thing lying over the page. Paint inside the sheet can only
            // light the sheet, so the lift has to be the system's shadow: that needs an
            // outline to cast around, and this panel has never had one.
            surface.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRect(0, 0, view.getWidth(), view.getHeight());
                }
            });
            surface.setElevation(dp(activity, 10));
            // ONE shape, set once: the surface is a slab with the same radius whether it is
            // a bar or a full dashboard. It used to swap between a capsule and a slab as it
            // opened, and a shape that changes halfway is what made it read as two boxes.
            GradientDrawable slab = new GradientDrawable();
            slab.setColor(SlothTheme.surface());
            int topRadius = dp(activity, 26);
            int bottomRadius = dp(activity, 26);
            slab.setCornerRadii(new float[]{topRadius, topRadius, topRadius, topRadius,
                    bottomRadius, bottomRadius, bottomRadius, bottomRadius});
            surface.setBackground(slab);
            surfaceView = surface;
            FrameLayout.LayoutParams surfaceLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP);
            // the same inset at every height: one card that grows, never a bar that becomes a
            // sheet. The margins are the only thing that makes a floating panel read as
            // floating, and they do not change as it opens.
            int inset = dp(activity, 6);
            surfaceLp.leftMargin = inset;
            surfaceLp.rightMargin = inset;
            surface.setLayoutParams(surfaceLp);
            surfaceSized = false;
            // The status bar strip belongs to the app, and on Android 15 the platform paints
            // it edge to edge: leaving it to the system gives a band of a different colour
            // above a panel that is ours. Paint it in the panel's own colour and tell the
            // platform which way the glyphs must point.
            View scrim = new View(activity);
            scrim.setTag("status-scrim");
            scrim.setBackgroundColor(SlothTheme.surface());
            scrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams scrimLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(0, statusInset), Gravity.TOP);
            root.addView(scrim, scrimLp);
            statusScrim = scrim;
            paintStatusBar(activity);
            root.addView(surface);

            // the scene sits behind the whole panel, so the bar and the drawer are the
            // same colour at the join - the seam was the sky showing only under the drawer
            backdropView = new SlothBackdrop(activity);
            surface.addView(backdropView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            LinearLayout column = new LinearLayout(activity);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setTag("column");
            surface.addView(column, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets insets) {
                    int bottom = android.os.Build.VERSION.SDK_INT >= 30
                            ? insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
                            : insets.getSystemWindowInsetBottom();
                    applyBottomInset(activity, bottom);
                    return insets;
                }
            });

            LinearLayout bar = buildBar(activity);
            bar.setTag("bar");
            barView = bar;

            // ------------------------------------------------- the bar drags itself
            // No handle and no scrim: the bar is the affordance. SlothBar intercepts
            // only once the gesture is unmistakably vertical, so taps on the address
            // field and sideways swipes still belong to the children.
            SlothBar barWrap = new SlothBar(activity);
            barWrap.setTag("drag");
            barWrapView = barWrap;
            barWrap.addView(bar, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            column.addView(barWrap, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            // no rule between the bar and the dashboard: a divider is the cheapest way to
            // say "two things", which is the opposite of what this surface is

            final SlothBar.Listener shellDrag = new SlothBar.Listener() {
                @Override
                public void onDragStart(float y, float carry) {
                    // a flick may still be flying when the finger lands. Two writers to the
                    // same height is the drawer fighting the hand that grabbed it
                    animating = false;
                    gen++;
                    downY = y;
                    int h = drawer == null ? 0 : drawer.getLayoutParams().height;
                    // the sheet is under the finger: it takes up the travel spent claiming
                    // the gesture, so nothing lurches when the drag begins
                    startH = Math.max(0, h + Math.round(carry));
                    dragging = true;
                    traceFrom = h;
                    tracePeak = Math.round(startH);
                    traceMoves = 0;
                    traceVelocity = 0f;
                    traceTo = -1;
                    traceFlicked = false;
                    traceMs = android.os.SystemClock.uptimeMillis();
                }

                @Override
                public void onDrag(float dy) {
                    if (drawer == null) {
                        return;
                    }
                    // a digitiser can deliver two moves before a frame is drawn. Keep the
                    // newest and apply it once per loop turn: the sheet still sits under the
                    // finger, and the tree is measured once instead of twice
                    dragPending = (int) Math.max(0,
                            Math.min(startH + dy, fullHeight(activity)));
                    if (!dragPosted) {
                        dragPosted = true;
                        MAIN.post(new Runnable() {
                            @Override
                            public void run() {
                                dragPosted = false;
                                if (dragging && dragPending != Integer.MIN_VALUE) {
                                    int h = dragPending;
                                    dragPending = Integer.MIN_VALUE;
                                    traceMoves++;
                                    tracePeak = h;
                                    setDrawerHeight(activity, h);
                                }
                            }
                        });
                    }
                }

                @Override
                public boolean onSwipe(float dx) {
                    return swipeTab(activity, dx < 0);
                }

                @Override
                public void onRelease(float velocity) {
                    // only the view that claimed the drag may end it. A scrollable child
                    // reporting its own release used to settle a sheet nobody had touched,
                    // which is one way the drawer appeared to shut itself
                    if (!dragging) {
                        return;
                    }
                    // whatever the last turn had not applied yet, applied now, so the settle
                    // starts from where the finger left the sheet and not one frame behind it
                    if (dragPending != Integer.MIN_VALUE) {
                        int h = Math.max(0, Math.min(dragPending, fullHeight(activity)));
                        dragPending = Integer.MIN_VALUE;
                        setDrawerHeight(activity, h);
                    }
                    dragging = false;
                    traceVelocity = velocity;
                    traceFlicked = Math.abs(velocity) > 0.55f;
                    traceMs = android.os.SystemClock.uptimeMillis() - traceMs;
                    // the commit: the page takes its real offset once, now that the finger is up
                    if (appRootPadTop >= 0) {
                        alignPageToBar(activity, drawer == null ? 0 : drawer.getLayoutParams().height, false);
                    }
                    finishDrag(activity, velocity);
                }

                @Override
                public void onTap() {
                    if (drawer == null) {
                        return;
                    }
                    boolean open = drawer.getLayoutParams().height > dp(activity, 24);
                    if (!open) {
                        leaveSettings(activity);
                    }
                    animateTo(activity, open ? 0 : openHeight(activity));
                }
            };
            barWrap.setListener(shellDrag);

            // ----------------------------------------------------------- drawer
            FrameLayout sheet = new FrameLayout(activity);
            FrameLayout.LayoutParams sheetLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0);
            // inside the surface the bar already supplies the offset, so no margin
            sheet.setTag("drawer");
        sheetView = sheet;
            sheet.setLayoutParams(sheetLp);
            sheet.setBackground(null);
        sheet.setClipToOutline(true);
        // the phone's navigation bar sits at the bottom of the screen; the drawer
        // keeps its footer above it instead of letting the buttons sit underneath
        sheet.setPadding(0, 0, 0, navInset(activity));

        // between the resting sizes the drawer is mid-drag and will cut something in
        // half. A short fade at its bottom edge makes that read as "more below" rather
        // than as a broken card.
            sheet.setVisibility(View.GONE);

            // the reworked dashboard owns the drawer surface
            LinearLayout holder = new LinearLayout(activity);
            holder.setOrientation(LinearLayout.VERTICAL);
            holder.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);

            SlothSheetScroll list = new SlothSheetScroll(activity);
            list.setListener(shellDrag);
            list.setTag("list");
            scroller = list;
            SlothLog.installCrash(activity.getApplicationContext());
            installBack(activity);
            every(800, new Runnable() {
                @Override
                public void run() {
                    tick();
                }
            });
            board = new SlothBoard(activity);
            board.onSite(thisSite(activity));
            board.onTabCount(tabCount(activity));
            tintStatusBar(activity);
            refinePeek(activity);
            board.onTabPicked(new Runnable() {
                @Override
                public void run() {
                    animateTo(activity, 0);
                }
            });
            peekRow = new LinearLayout(activity);
            peekRow.setOrientation(LinearLayout.HORIZONTAL);
            peekRow.setGravity(Gravity.CENTER_VERTICAL);
            peekRow.setPadding(0, dp(activity, 2), 0, dp(activity, 2));
            peekScroll = new HorizontalScrollView(activity);
            peekScroll.setHorizontalScrollBarEnabled(false);
            peekScroll.setVerticalScrollBarEnabled(false);
            peekScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            peekScroll.setTag("peek-strip");
            peekScroll.addView(peekRow, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            peekScroll.setVisibility(View.GONE);
            // sideways walks the tabs; up and down belongs to the sheet. The listener goes
            // on the scroll container AND every square inside it: a touch that lands on a
            // tab is delivered to the tab, and the container's own listener never saw the
            // gesture - which is why a vertical drag only worked on the gaps between tabs.
            stripDrag = new SlothDrag(shellDrag);
            peekScroll.setOnTouchListener(stripDrag);
            holder.addView(peekScroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            scroller.addView(board.view());
            board.setDragSink(shellDrag);
            board.setEditSink(new Runnable() {
                @Override
                public void run() {
                    if (widgetSheet != null) {
                        widgetSheet.bringToFront();
                        widgetSheet.setVisibility(View.VISIBLE);
                    }
                }
            });
            scroller.setFillViewport(true);
            holder.addView(scroller, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            LinearLayout foot = new LinearLayout(activity);
            foot.setOrientation(LinearLayout.HORIZONTAL);
            foot.setPadding(0, dp(activity, 6), 0, dp(activity, 12));
            // the mock's footer: Settings on the left, widgets on the right, and the
            // palette in between - the one control that changes the whole surface
            View themeChip = chip(activity, SlothTheme.name(), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (paletteSheet != null) {
                        boolean showing = paletteSheet.getVisibility() == View.VISIBLE;
                        if (!showing) {
                            paletteSheet.bringToFront();
                        }
                        paletteSheet.setVisibility(showing ? View.GONE : View.VISIBLE);
                    }
                }
            });
            themeChip.setTag("palette-chip");
            foot.addView(themeChip, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            View settingsChip = chip(activity, "Settings", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openSettingsPage(activity);
                }
            });
            foot.addView(settingsChip, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            View addChip = chip(activity, "Add widget", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (widgetSheet != null) {
                        widgetSheet.bringToFront();
                        widgetSheet.setVisibility(View.VISIBLE);
                    }
                }
            });
            foot.addView(addChip, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            widgetSheet = widgetChooser(activity);
            sheet.addView(widgetSheet);
            settingsPage = SlothSettings.build(activity, new Runnable() {
                @Override
                public void run() {
                    if (settingsPage != null) {
                        settingsPage.setVisibility(View.GONE);
                    }
                    rebuild(activity);
                }
            }, new Runnable() {
                // a row that opens one of the browser's screens must get out of the way,
                // or the screen opens underneath a page that still covers it
                @Override
                public void run() {
                    if (settingsPage != null) {
                        settingsPage.setVisibility(View.GONE);
                    }
                }
            });
            // full screen, above the surface: the drawer closes when Settings opens, and a
            // page parented to a closed drawer is clipped to nothing - which is what the
            // first render of this screen showed
            root.addView(settingsPage, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            settingsPage.setVisibility(View.GONE);
            libraryPage = SlothLibrary.build(activity, new SlothLibrary.Sink() {
                @Override
                public void openUrl(String url) {
                    if (libraryPage != null) {
                        libraryPage.setVisibility(View.GONE);
                    }
                    gotoUrl(activity, url);
                }
            }, new Runnable() {
                @Override
                public void run() {
                    if (libraryPage != null) {
                        libraryPage.setVisibility(View.GONE);
                    }
                }
            });
            root.addView(libraryPage, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            libraryPage.setVisibility(View.GONE);
            findBar = SlothFind.build(activity, new Runnable() {
                @Override
                public void run() {
                    closeFind(activity);
                }
            });
            FrameLayout.LayoutParams findLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            // under the bar, not over it: the address stays readable while a search is open
            findLp.topMargin = dp(activity, 64) + statusInset;
            root.addView(findBar, findLp);
            findBar.setVisibility(View.GONE);
            diagPage = SlothDiag.page(activity);
            root.addView(diagPage, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            diagPage.setVisibility(View.GONE);
            paletteSheet = palettePicker(activity);
            footRow = foot;
            // no fade band and no grabber inside the sheet: the header above it is the
            // handle, and a fade says the panel is cut rather than continued
            holder.addView(foot);
            sheet.addView(holder, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (holder.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                // the content starts at the top of the sheet: the 20dp band above it was
                // there for a grabber, and a band of panel with nothing in it is exactly the
                // empty strip that keeps being reported
                ((FrameLayout.LayoutParams) holder.getLayoutParams()).topMargin = 0;
            }
            // the seam: a shadow and a hinge line, drawn over everything in the sheet so
            // the drawer reads as a surface lifted over the page rather than a flat panel
            // No hinge line, no grabber band, no fade: the surface is one shape with one
            // fill, so the seam between the header and the dashboard is not drawn at all.
            if (paletteSheet != null) {
                // added last so it covers the drawer content rather than sitting under it
                sheet.addView(paletteSheet);
            }
            column.addView(sheet);
            drawer = sheet;

            statusInset = statusBarHeight(activity);
            // the strip was built before the insets were known: paint it to the height we
            // just measured, or it stays a zero-height band for the life of the window
            if (statusScrim != null) {
                paintStatusBar(activity);
            }
            // the peek is the strip: grip, one row. refinePeek() may adjust it once the
            // board has measured itself, but the ceiling is the strip, not a dashboard
            peekPx = dp(activity, 72);

            // everything in the shell starts below the system status bar. Inside the
            // surface the bar needs no margin: the surface carries the status-bar gap,
            // and the drawer simply follows the bar.
            surface.setPadding(0, dp(activity, 8) + statusInset, 0, 0);
            int barHeight = bar.getLayoutParams() != null && bar.getLayoutParams().height > 0
                    ? bar.getLayoutParams().height : dp(activity, 56);
            barWrap.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, barHeight));
            // the surface is exactly the bar until the drawer grows it: never a wrap, which
            // would measure the backdrop against the whole window
            surfaceBarPx = barHeight + dp(activity, 8) + statusInset;
            applySurfaceHeight(activity, 0);
            sheet.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0));


            // collapse the original chrome and drawer: the new shell replaces both.
            // The chrome is never lent out - an empty strip of it under the bar is
            // exactly the "weird box" that shows up when it is left laid out.
            for (String name : CHROME) {
                collapse(activity, name);
            }
            if (!settingsOpen) {
                for (String name : PANEL) {
                    collapse(activity, name);
                }
            }

            // whatever the app laid out underneath is the page; it gets pushed down
            // so the bar never covers the top of a site
            for (int i = 0; i < content.getChildCount(); i++) {
                View child = content.getChildAt(i);
                if (child != root) {
                    appRoot = child;
                    break;
                }
            }
            if (appRoot != null) {
                if (appRootPadTop < 0) {
                    appRootPadTop = appRoot.getPaddingTop();
                    // the window now runs under the nav bar, so the page has to keep its own
                    // distance from it or the last line of every site sits under the pill
                    appRootPadBottom = Math.max(appRoot.getPaddingBottom(), navInset(activity));
                    appRoot.setPadding(appRoot.getPaddingLeft(), appRoot.getPaddingTop(),
                            appRoot.getPaddingRight(), appRootPadBottom);
                }
                alignPageToBar(activity, 0);
                root.post(new Runnable() {
                    @Override
                    public void run() {
                        if (owner == activity) {
                            alignPageToBar(activity, drawer == null ? 0
                                    : drawer.getLayoutParams().height);
                        }
                    }
                });
            }

            content.addView(root);
            probeCover(activity, "shell ready");
            root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    if (owner == activity) {
                        releaseShell();
                    }
                }
            });
            overlay = root;

            // the old bar is gone for good; clear any preference left over from
            // the builds that let you bring it back, so it cannot resurrect itself
            try {
                activity.getSharedPreferences("slothworks", Context.MODE_PRIVATE)
                        .edit().remove("originalBar").apply();
            } catch (Throwable ignored) {
            }
            refresh(activity);
        } catch (Throwable t) {
            if (DEBUG) {
                t.printStackTrace();
            }
        }
    }


    /* ------------------------------------------------------------------- bar */
    /**
     * The redesigned top bar: one capsule, an address pill inset inside it, and
     * actions that fade when the browser says they cannot be used.
     */
    private static LinearLayout buildBar(final Activity activity) {
        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(activity, 4), 0, dp(activity, 4), 0);
        bar.setElevation(dp(activity, 6));
        // the surface owns the only fill: a bar with its own capsule draws a second
        // rounded rect inside the panel, which is a bar inside a box again
        bar.setBackground(null);

        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 56), Gravity.TOP);
        barLp.setMargins(dp(activity, 8), dp(activity, 8), dp(activity, 8), 0);
        bar.setLayoutParams(barLp);

        backGlyph = barIcon(activity, SlothIcons.Kind.BACK, "Back", "btnBack", 30);
        fwdGlyph = barIcon(activity, SlothIcons.Kind.FORWARD, "Forward", "btnFwd", 30);
        bar.addView(backGlyph);
        bar.addView(fwdGlyph);
        bar.addView(addressPill(activity));

        View tabs = tabChip(activity);
        bar.addView(tabs);
        View reload = barIcon(activity, SlothIcons.Kind.RELOAD, "Reload", "btnReload", 30);
        reload.setLongClickable(true);
        reload.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return stop(activity);
            }
        });
        bar.addView(reload);
        View newTab = barIcon(activity, SlothIcons.Kind.PLUS, "New tab", "btnPlus", 30);
        bar.addView(newTab);
        // the three dots open the drawer rather than the app's own menu: that menu
        // draws into the panel we keep collapsed, and this shell is meant to stand alone
        View menu = barIcon(activity, SlothIcons.Kind.MENU, "Menu", "btnMenu", 30);
        menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                animateTo(activity, openHeight(activity));
            }
        });
        bar.addView(menu);
        chromeViews.clear();
        chromeViews.add(backGlyph);
        chromeViews.add(fwdGlyph);
        chromeViews.add(pillOf(bar));
        chromeViews.add(tabs);
        chromeViews.add(reload);
        chromeViews.add(newTab);
        chromeViews.add(menu);
        return bar;
    }

    /** The address field, found once so the fade does not have to know its position. */
    private static View pillOf(ViewGroup bar) {
        for (int i = 0; i < bar.getChildCount(); i++) {
            View child = bar.getChildAt(i);
            if (child.getContentDescription() == null && child.isClickable()) {
                return child;
            }
        }
        return null;
    }

    /** A count you can tap, so the drawer is not only reachable by the handle. */
    private static View tabChip(final Activity activity) {
        LinearLayout chip = new LinearLayout(activity);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER);
        chip.setContentDescription("Tabs");
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setBackground(pressState(activity));
        chip.setLayoutParams(new LinearLayout.LayoutParams(dp(activity, 40), dp(activity, 44)));
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                animateTo(activity, openHeight(activity));
            }
        });
        // the tab count opens the drawer; holding it opens the app's own switcher
        chip.setLongClickable(true);
        chip.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                try {
                    call(activity, "showSwitcher", new Class<?>[0], new Object[0]);
                    return true;
                } catch (Throwable ignored) {
                    return false;
                }
            }
        });

        TabFace = new TextView(activity);
        TabFace.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        TabFace.setTextColor(INK);
        TabFace.setTypeface(TabFace.getTypeface(), android.graphics.Typeface.BOLD);
        TabFace.setGravity(Gravity.CENTER);
        TabFace.setText("1");
        // a number alone reads as a stray counter; a stack of tabs beside it says what is
        // being counted without spending a word
        LinearLayout badge = new LinearLayout(activity);
        badge.setOrientation(LinearLayout.HORIZONTAL);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(round(activity, 8, SlothTheme.lineAt(0x1C), 0));
        SlothIcons stack = SlothIcons.of(activity, SlothIcons.Kind.TABS, 9, SlothTheme.dim());
        badge.addView(stack);
        LinearLayout.LayoutParams faceLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        faceLp.leftMargin = dp(activity, 3);
        badge.addView(TabFace, faceLp);
        chip.addView(badge, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 24)));
        return chip;
    }
/** When the last cover probe ran, so a busy screen does not fill the log with probes. */
    private static long lastProbe;

    /**
     * Ask, shortly after a page should be up, whether anything is drawn over it.
     *
     * <p>Delayed because a page that has just been asked for has not been laid out yet, and
     * probed on the main thread because the answer is about views.</p>
     */
    static void probeCover(final Activity activity, final String when) {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastProbe < 4000) {
            return;
        }
        lastProbe = now;
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (owner != activity) {
                    return;
                }
                SlothCover.probe(activity, when);
            }
        }, 1200);
    }

    static boolean navigate(Activity activity, EditText field) {
        // a page you asked to leave should not stay on top of the page you asked to open
        hideSettingsPage(activity);
        String wanted = field.getText() == null ? "" : field.getText().toString();
        // the address is typed into a mirror of the app's own omnibox. The app's routine
        // reads the field it is given, but the app also keeps its own widget - and a mirror
        // that has drifted from it is how you type an address and have nothing happen.
        field = syncOmnibox(activity, field);
        SlothLog.note("navigating: " + wanted);
        probeCover(activity, "after " + wanted);
        try {
            java.lang.reflect.Method go = Class.forName("com.sloth.browser.DrawerFace")
                    .getDeclaredMethod("go", Activity.class, EditText.class);
            go.setAccessible(true);
            go.invoke(null, activity, field);
            // and the panel you typed from gets out of the way of the page it asked for
            closeDrawerFor(activity);
            return true;
        } catch (Throwable t) {
            SlothLog.note("navigation failed: " + t.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * The app's settings screen is built into panelBody, so the shell has to stop
     * hiding that panel while it is open - otherwise the screen is drawn into a
     * view with no size and nothing appears.
     */
    private static TextView text(Activity activity, String value, float size, int colour, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setTextColor(colour);
        if (bold) {
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return view;
    }

    /** A rounded box with an optional hairline, in the active palette. */
    static GradientDrawable box(Activity activity, float radius, int fill, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(activity, radius));
        shape.setColor(fill);
        if (stroke != 0) {
            shape.setStroke(Math.max(1, dp(activity, 1)), stroke);
        }
        return shape;
    }

    /** Add widget: the five things the drawer can hold, with the ones on it ticked. */
    private static View widgetChooser(final Activity activity) {
        FrameLayout cover = new FrameLayout(activity);
        cover.setTag("widgets");
        cover.setBackgroundColor(SlothTheme.surface());
        cover.setVisibility(View.GONE);
        cover.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(activity, 16), dp(activity, 10), dp(activity, 16), dp(activity, 10));

        LinearLayout head = new LinearLayout(activity);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(activity, "Widgets", 13f, SlothTheme.muted(), true);
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = text(activity, "Close", 13f, SlothTheme.ink(), false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close the widget list");
        close.setBackground(box(activity, 999, 0, SlothTheme.lineAt(0x33)));
        close.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (widgetSheet != null) {
                    widgetSheet.setVisibility(View.GONE);
                }
            }
        });
        head.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 36)));
        column.addView(head);

        for (final String kind : SlothBoard.KINDS) {
            final boolean shown = board != null && !board.isHidden(kind);
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
            row.setClickable(true);
            row.setFocusable(true);
            row.setTag("widget-row");
            row.setContentDescription(SlothBoard.kindLabel(kind));
            row.setBackground(box(activity, 14, SlothTheme.card(),
                    shown ? SlothTheme.lineAt(0x8C) : SlothTheme.lineAt(0x1A)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 48));
            lp.bottomMargin = dp(activity, 8);
            row.setLayoutParams(lp);

            row.addView(text(activity, SlothBoard.kindLabel(kind), 14f, SlothTheme.ink(), false),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(text(activity, shown ? "\u2713" : "+", 15f, SlothTheme.accent(), true));
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (board != null) {
                        board.toggleWidget(kind);
                    }
                    // the drawer's own content changes, so the surface is rebuilt - but the
                    // drawer was open when this was tapped, so it comes back at the same
                    // height with the list still on show rather than shutting in the face
                    // of the person using it
                    final int wasOpen = drawer == null ? 0 : drawer.getLayoutParams().height;
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            if (owner != activity) {
                                return; // the window that asked for this is gone
                            }
                            rebuild(activity);
                            if (wasOpen > 0) {
                                setDrawerHeight(activity, wasOpen);
                                openWidgetChooser(activity);
                            }
                        }
                    });
                }
            });
            column.addView(row);
        }
        cover.addView(column);
        return cover;
    }

    /** Every palette, with its own colours on show, so the choice is not a word. */
    private static View palettePicker(final Activity activity) {
        FrameLayout cover = new FrameLayout(activity);
        cover.setTag("palette");
        cover.setBackgroundColor(SlothTheme.surface());
        cover.setVisibility(View.GONE);
        cover.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(activity, 16), dp(activity, 10), dp(activity, 16), dp(activity, 10));

        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(activity);
        title.setText("Palette");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        title.setTextColor(SlothTheme.dim());
        title.setLetterSpacing(0.14f);
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(activity);
        close.setText("Close");
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        close.setTextColor(SlothTheme.ink());
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close the palette list");
        close.setBackground(box(activity, 999, 0, SlothTheme.lineAt(0x33)));
        close.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (paletteSheet != null) {
                    paletteSheet.setVisibility(View.GONE);
                }
            }
        });
        head.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 36)));
        column.addView(head);

        final ScrollView scroller = new ScrollView(activity);
        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, dp(activity, 6), 0, 0);

        for (int i = 0; i < SlothTheme.NAMES.length; i++) {
            rows.addView(paletteRow(activity, i));
        }
        scroller.addView(rows);
        column.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        cover.addView(column);
        return cover;
    }

    private static View paletteRow(final Activity activity, final int which) {
        final LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
        String full = SlothTheme.FULL[which];
        row.setContentDescription(full);
        row.setTag("palette-row");
        row.setClickable(true);
        row.setFocusable(true);
        boolean current = which == SlothTheme.index();
        row.setBackground(box(activity, 14, SlothTheme.card(),
                current ? SlothTheme.lineAt(0x8C) : SlothTheme.lineAt(0x1A)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 52));
        lp.bottomMargin = dp(activity, 8);
        row.setLayoutParams(lp);

        TextView name = new TextView(activity);
        name.setText(full);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        name.setTextColor(SlothTheme.ink());
        row.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (current) {
            // a tick, not just a tinted outline: the selected row has to be obvious
            TextView tick = text(activity, "\u2713", 15f, SlothTheme.accent(), true);
            tick.setContentDescription("Current palette");
            LinearLayout.LayoutParams tickLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tickLp.rightMargin = dp(activity, 8);
            row.addView(tick, tickLp);
        }

        int[] swatches = {SlothTheme.bg(which), SlothTheme.card(which), SlothTheme.accent(which)};
        for (int colour : swatches) {
            View dot = new View(activity);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(colour);
            shape.setStroke(Math.max(1, dp(activity, 1)), SlothTheme.lineAt(0x33));
            dot.setBackground(shape);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                    dp(activity, 16), dp(activity, 16));
            dotLp.leftMargin = dp(activity, 6);
            row.addView(dot, dotLp);
        }

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SlothTheme.select(activity, which);
                // the choice is made, so the drawer gets out of the way: close it first,
                // then rebuild the surface in the new palette next frame
                if (paletteSheet != null) {
                    paletteSheet.setVisibility(View.GONE);
                }
                if (drawer != null) {
                    setDrawerHeight(activity, 0);
                    openIntent = false;
                }
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (owner != activity) {
                            return; // the window that asked for this is gone
                        }
                        rebuild(activity);
                    }
                });
            }
        });
        return row;
    }

    /**
     * Open one of the app's own screens.
     *
     * <p>These used to be borrowing the app's panel, which meant a box of the app's own
     * chrome appeared with the page behind it - the "old box". The page the shell draws
     * carries the same destinations now, so the panel is never lent out and the request
     * lands on our page instead.</p>
     */
    static void openPanel(Activity activity, String kind) {
        closeDrawerFor(activity);
        if (!"bookmarks".equals(kind) && !"history".equals(kind) && !"downloads".equals(kind)) {
            SlothLog.note("panel " + kind + " opened on our page");
            openSettingsPage(activity);
            return;
        }
        // these three are lists the browser builds itself: the panel is the only place they
        // can draw. Lend it, then look: a panel the app did not fill is the empty box again.
        lendPanel(activity);
        int before = panelContentCount(activity);
        try {
            call(activity, "openPanel", new Class<?>[]{String.class}, new Object[]{kind});
        } catch (Throwable t) {
            SlothLog.note("openPanel(" + kind + ") failed: " + t.getClass().getSimpleName());
            releasePanel(activity);
            return;
        }
        verifyLoan(activity, kind, before);
    }

    /**
     * Bookmarks and history in our own page, drawn from the engine's records.
     *
     * <p>The panel loan is the fallback, not the plan: when the engine will not answer there is
     * nothing to list, and lending the app its panel at least gives it a chance to draw.</p>
     */
    static void openLibrary(Activity activity, String kind) {
        closeDrawerFor(activity);
        View page = libraryPage;
        if (page == null || page.getContext() != activity) {
            View found = findTagged((ViewGroup) activity.findViewById(android.R.id.content),
                    "library-page");
            page = found != null ? found : page;
            libraryPage = page;
        }
        if (page == null || !SlothLibrary.show(page, kind)) {
            SlothLog.note("no library page in this window; lending the panel instead");
            openPanel(activity, kind);
            return;
        }
        page.bringToFront();
        page.setVisibility(View.VISIBLE);
        SlothLog.note("library opened: " + kind);
    }

    /**
     * Open a page the library listed.
     *
     * <p>Through the omnibox and navigate(), the path the address pill already uses - writing
     * the app's own field and letting the browser's routine read it keeps the app's widget and
     * the page in step. The activity's own go() is the fallback, not the plan.</p>
     */
    static void gotoUrl(Activity activity, String url) {
        View real = resolve(activity, "urlBar");
        if (real instanceof EditText) {
            EditText field = (EditText) real;
            field.setText(url);
            field.setSelection(field.getText().length());
            if (navigate(activity, field)) {
                SlothLog.note("library opened " + url);
                return;
            }
        }
        try {
            call(activity, "go", new Class<?>[]{String.class}, new Object[]{url});
            SlothLog.note("library opened " + url + " (direct)");
        } catch (Throwable t) {
            SlothLog.note("library could not open " + url + ": " + t.getClass().getSimpleName());
            openDrawer(activity);
        }
    }

    /** Hand the app its panel at a real height, so its own lists have somewhere to draw. */
    static void lendPanel(Activity activity) {
        settingsOpen = true;
        for (String name : PANEL) {
            View v = resolve(activity, name);
            if (v == null) {
                continue;
            }
            int[] st = legacyState.get(name);
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp != null && st != null) {
                lp.height = st[0];
                v.setLayoutParams(lp);
            }
            v.setVisibility(View.VISIBLE);
        }
        ensurePanelRoom(activity);
    }
/**
     * Ask the app to close its own panel.
     *
     * <p>The browser collapses its page view to nothing while a panel is up - that is how
     * the panel gets the whole screen. If the panel is taken away without telling it, the
     * page stays 1280x0: a black window with an address in it. closePanel() is what restores
     * it, so every release of the loan calls it.</p>
     */
    static void closeAppPanel(Activity activity) {
        try {
            call(activity, "closePanel", new Class<?>[]{}, new Object[]{});
            SlothLog.note("app panel closed");
        } catch (Throwable t) {
            SlothLog.warn("no way to close the app panel: " + t.getClass().getSimpleName());
        }
        ensurePageVisible(activity);
    }

    /**
     * A page view with no height is a black page.
     *
     * <p>Checked after anything that could have collapsed it. If the app left the page at
     * zero height and is not showing a panel, this puts it back and says so, because the
     * alternative is the owner photographing a black rectangle again.</p>
     */
    static void ensurePageVisible(Activity activity) {
        View page = SlothCover.page(activity);
        if (page == null || page.getHeight() > 0 || page.getVisibility() != View.VISIBLE) {
            return;
        }
        ViewGroup.LayoutParams lp = page.getLayoutParams();
        if (lp != null && lp.height == 0) {
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            page.setLayoutParams(lp);
            page.requestLayout();
            SlothLog.warn("page was 0px tall; asked for the full height");
        }
    }

    /**
     * How much the app has put in its panel, counted all the way down.
     *
     * <p>Counting only the panel body's immediate children missed a list drawn inside a
     * scroller - the app had filled the screen and the count still read zero, which is how
     * a working Bookmarks screen was reported as "nothing drew".</p>
     */
    static int panelContentCount(Activity activity) {
        View body = resolve(activity, "panelBody");
        if (body == null) {
            body = resolve(activity, "panel");
        }
        return body == null ? 0 : countViews(body, body);
    }

    private static int countViews(View view, View root) {
        int total = view == root ? 0 : 1;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                total += countViews(group.getChildAt(i), root);
            }
        }
        return total;
    }

    /**
     * Look, a moment later, at whether the app actually drew its list.
     *
     * <p>A lent panel that stays empty is the box the owner complained about, so the loan is
     * checked rather than assumed: if the count of views in the panel has not moved, the
     * panel is closed and the report says which screen drew nothing.</p>
     */
    static void verifyLoan(final Activity activity, final String kind, final int before) {
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (owner != activity) {
                    return;
                }
                // drawing means the app added views of its own, not that a body exists
                boolean drew = panelContentCount(activity) > before;
                if (!drew) {
                    SlothLog.note("nothing drew in the panel for " + kind + "; closed it");
                    releasePanel(activity);
                    openSettingsPage(activity);
                } else {
                    SlothLog.note("the app drew " + kind + " in the panel");
                }
            }
        }, 700);
    }

    /** Palettes change the whole surface, so the shell is rebuilt rather than repainted. */
    static void rebuild(Activity activity) {
        try {
            if (overlay != null && overlay.getParent() instanceof ViewGroup) {
                ((ViewGroup) overlay.getParent()).removeView(overlay);
            }
        } catch (Throwable ignored) {
        }
        overlay = null;
        gen++;
        animating = false;
        stopLoops();
        own(activity);
    }
/**
     * The app's screens draw underneath our panel. Leaving the drawer open would put it
     * on top of the thing the tap just opened - which is exactly what "Settings does not
     * appear" looks like.
     */
    private static void closeDrawerFor(Activity activity) {
        openIntent = false;
        // anything still travelling was travelling somewhere else
        gen++;
        if (drawer != null && drawer.getLayoutParams().height > 0) {
            setDrawerHeight(activity, 0);
        }
    }

    /**
     * A request for the app's settings screen, answered with ours.
     *
     * <p>The app's own screen draws into its panel, and that panel is the box that used to
     * sit empty over the page. Our page carries the same switches and can actually be read,
     * so this opens it and leaves the panel shut.</p>
     */
    static void openAppSettings(Activity activity, String section) {
        closeDrawerFor(activity);
        releasePanel(activity);
        SlothLog.note("app settings (" + (section == null || section.isEmpty() ? "top" : section)
                + ") opened on our page");
        openSettingsPage(activity);
    }

    /**
     * A panel with no height is a blank screen.
     *
     * <p>The heights the shell remembers are the heights the app's own chrome had when it
     * was put away - and chrome that was already hidden was remembered as zero. Lending
     * those back gives the app nowhere to draw, which reads as a blank page. So after a
     * screen is opened the panel is given a real height, once, on the next frame.</p>
     */
    static void ensurePanelRoom(Activity activity) {
        final Activity target = activity;
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                if (owner != target) {
                    return;
                }
                if (!settingsOpen) {
                    // the loan ended before this frame arrived: a panel shown now would be
                    // the empty box, delivered one frame after we put it away
                    return;
                }
                View panel = resolve(target, "panel");
                if (panel == null) {
                    SlothLog.note("no panel to lend room to");
                    return;
                }
                ViewGroup.LayoutParams lp = panel.getLayoutParams();
                if (lp != null && (lp.height == 0 || panel.getHeight() == 0)) {
                    lp.height = panelHeight(target);
                    panel.setLayoutParams(lp);
                }
                panel.setVisibility(View.VISIBLE);
                View body = resolve(target, "panelBody");
                if (body != null) {
                    body.setVisibility(View.VISIBLE);
                    ViewGroup.LayoutParams blp = body.getLayoutParams();
                    if (blp != null && blp.height == 0) {
                        blp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                        body.setLayoutParams(blp);
                    }
                }
            }
        });
    }

    /** Give a picker somewhere to appear when it is opened from outside the drawer. */
    private static void ensureDrawerOpen(Activity activity) {
        if (drawer != null && drawer.getLayoutParams().height <= 0) {
            setDrawerHeight(activity, Math.max(dp(activity, 240), peekPx));
            openIntent = true;
        }
    }

    /**
     * Whether the layers that are showing agree with each other.
     *
     * <p>Every bug reported against this shell has been an ordering fault: a page on top of
     * the screen it opened, a list inside a shut drawer, a panel with no height. None were
     * missing controls. This asks the views themselves, after a transition, whether what is
     * on screen makes sense - and returns the first thing that does not, or null.</p>
     */
    static String mislay() {
        boolean drawerUp = drawer != null && drawer.getLayoutParams() != null
                && drawer.getLayoutParams().height > 0;
        if (settingsPage != null && settingsPage.getVisibility() == View.VISIBLE && drawerUp) {
            return "the settings page is up over an open drawer";
        }
        if (paletteSheet != null && paletteSheet.getVisibility() == View.VISIBLE && !drawerUp) {
            return "the palette list is up inside a shut drawer";
        }
        if (widgetSheet != null && widgetSheet.getVisibility() == View.VISIBLE && !drawerUp) {
            return "the widget list is up inside a shut drawer";
        }
        if (settingsPage != null && settingsPage.getVisibility() == View.VISIBLE) {
            View panel = owner == null ? null : resolve(owner, "panel");
            if (panel != null && panel.getVisibility() == View.VISIBLE && panel.getHeight() > 0) {
                return "the settings page is covering a screen the app opened";
            }
            ViewGroup parent = (ViewGroup) settingsPage.getParent();
            if (parent != null && parent.getChildAt(parent.getChildCount() - 1) != settingsPage) {
                return "something is drawn over the settings page";
            }
        }
        return null;
    }

    /** Record a transition that left the layers disagreeing, where a person can read it. */
    static void checkLayers(String when) {
        String breach = mislay();
        if (breach != null) {
            SlothLog.note(when + ": " + breach);
        }
    }

    /**
     * Screen privacy off, once.
     *
     * <p>The browser ships with screenshots blocked, which also blocks a person from
     * showing anyone what they are looking at - including this shell's own screens when
     * something looks wrong. It is cleared once per install and remembered, so turning it
     * back on by hand in Screen settings sticks instead of being undone at every launch.</p>
     */
    static boolean guardOff(Activity activity) {
        boolean done = false;
        try {
            android.content.SharedPreferences prefs =
                    activity.getSharedPreferences("slothworks", android.content.Context.MODE_PRIVATE);
            if (!prefs.getBoolean("screenGuardCleared", false)) {
                Object settings = settingsObject(activity);
                if (settings != null) {
                    java.lang.reflect.Field guard = settings.getClass().getDeclaredField("screenGuard");
                    guard.setAccessible(true);
                    guard.setBoolean(settings, false);
                    call(activity, "saveSettings", new Class[]{}, new Object[]{});
                    done = true;
                    if (done) {
                        prefs.edit().putBoolean("screenGuardCleared", true).apply();
                    }
                }
            }
        } catch (Throwable t) {
            SlothLog.note("could not clear the screen guard: " + t.getClass().getSimpleName());
        }
        try {
            activity.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        } catch (Throwable ignored) {
        }
        return done;
    }

    /** The app's settings model, found by type - its field name is the app's business. */
    static Object settingsModel(Activity activity) {
        return settingsObject(activity);
    }

    /** Ask the app to store whatever we changed. */
    static boolean saveSettings(Activity activity) {
        try {
            call(activity, "saveSettings", new Class[]{}, new Object[]{});
            return true;
        } catch (Throwable t) {
            SlothLog.note("saveSettings failed: " + t.getClass().getSimpleName());
            return false;
        }
    }

    private static Object settingsObject(Activity activity) {
        Class<?> type = activity.getClass();
        while (type != null) {
            for (java.lang.reflect.Field f : type.getDeclaredFields()) {
                if (f.getType().getName().equals("com.sloth.browser.engine.SlothSettings")) {
                    try {
                        f.setAccessible(true);
                        return f.get(activity);
                    } catch (Throwable ignored) {
                        return null;
                    }
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    /** The room the panel should have: everything below the bar and above the system bar. */
    private static int panelHeight(Activity activity) {
        int screen = activity.getResources().getDisplayMetrics().heightPixels;
        return Math.max(dp(activity, 240), screen - statusBarHeight(activity) - navInset(activity));
    }
/**
     * Register with the activity's back dispatcher, once per window.
     *
     * <p>Asked for by reflection because the shell is compiled against the platform only;
     * the app's own androidx classes are what actually answer at runtime.</p>
     */
    static void installBack(final Activity activity) {
        if (backInstalled.containsKey(activity)) {
            return;
        }
        backInstalled.put(activity, Boolean.TRUE);
        try {
            java.lang.reflect.Method getter = null;
            for (Class<?> type = activity.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    getter = type.getMethod("getOnBackPressedDispatcher");
                    break;
                } catch (NoSuchMethodException ignored) {
                    // keep walking up
                }
            }
            if (getter == null) {
                SlothLog.warn("no back dispatcher on this activity");
                return;
            }
            Object dispatcher = getter.invoke(activity);
            if (dispatcher == null) {
                return;
            }
            Class<?> callback = Class.forName("androidx.activity.OnBackPressedCallback");
            dispatcher.getClass().getMethod("addCallback", callback)
                    .invoke(dispatcher, new SlothBack(activity));
        } catch (Throwable t) {
            SlothLog.warn("no back dispatcher (" + t.getClass().getSimpleName()
                    + "); wrapping the window callback instead");
            installBackViaWindow(activity);
        }
    }

    /**
     * The fallback: sit in front of the window's own callback and answer the back key.
     *
     * <p>Only used when the activity has no back dispatcher to register with. Everything is
     * forwarded to the original callback, so this can only change what back does, never
     * what the rest of the window does.</p>
     */
    private static void installBackViaWindow(final Activity activity) {
        try {
            final android.view.Window window = activity.getWindow();
            if (window == null) {
                SlothLog.warn("back button left to the browser: no window");
                return;
            }
            final android.view.Window.Callback base = window.getCallback();
            if (base == null || callbackInstalled.containsKey(activity)) {
                SlothLog.warn("back button left to the browser: no callback to wrap");
                return;
            }
            callbackInstalled.put(activity, Boolean.TRUE);
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                    SlothShell.class.getClassLoader(),
                    new Class<?>[]{android.view.Window.Callback.class},
                    new java.lang.reflect.InvocationHandler() {
                        @Override
                        public Object invoke(Object self, java.lang.reflect.Method method,
                                             Object[] args) throws Throwable {
                            String name = method.getName();
                            if ("onBackPressed".equals(name) && backOneLayer(activity)) {
                                return null;
                            }
                            if ("dispatchKeyEvent".equals(name) && args != null && args.length == 1
                                    && args[0] instanceof android.view.KeyEvent) {
                                android.view.KeyEvent event = (android.view.KeyEvent) args[0];
                                if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK
                                        && event.getAction() == android.view.KeyEvent.ACTION_UP
                                        && backOneLayer(activity)) {
                                    return Boolean.TRUE;
                                }
                            }
                            try {
                                return method.invoke(base, args);
                            } catch (java.lang.reflect.InvocationTargetException wrapped) {
                                throw wrapped.getCause();
                            }
                        }
                    });
            window.setCallback((android.view.Window.Callback) proxy);
            SlothLog.note("back button wrapped at the window");
        } catch (Throwable t) {
            SlothLog.warn("back button left to the browser: " + t.getClass().getSimpleName());
        }
    }

    /**
     * Close one thing of ours, in the order a person would expect: the page on top, then
     * the drawer. False means nothing of ours is open.
     */
    static boolean backOneLayer(Activity activity) {
        // a page left over from a window that is gone is not this window's layer
        if (diagPage != null && diagPage.getContext() != activity) {
            diagPage = null;
        }
        if (settingsPage != null && settingsPage.getContext() != activity) {
            settingsPage = null;
        }
        if (diagPage != null && diagPage.getVisibility() == View.VISIBLE) {
            diagPage.setVisibility(View.GONE);
            SlothLog.note("back closed diagnostics");
            return true;
        }
        if (findBar != null && findBar.getContext() == activity
                && findBar.getVisibility() == View.VISIBLE) {
            closeFind(activity);
            return true;
        }
        if (libraryPage != null && libraryPage.getContext() == activity
                && libraryPage.getVisibility() == View.VISIBLE) {
            libraryPage.setVisibility(View.GONE);
            SlothLog.note("back closed the library");
            return true;
        }
        if (settingsPage != null && settingsPage.getVisibility() == View.VISIBLE) {
            settingsPage.setVisibility(View.GONE);
            SlothLog.note("back closed settings");
            checkLayers("back closed settings");
            return true;
        }
        if (paletteSheet != null && paletteSheet.getVisibility() == View.VISIBLE) {
            paletteSheet.setVisibility(View.GONE);
            return true;
        }
        if (widgetSheet != null && widgetSheet.getVisibility() == View.VISIBLE) {
            widgetSheet.setVisibility(View.GONE);
            return true;
        }
        if (settingsOpen) {
            // the browser's own panel is up, lent by us; back must put the page back.
            // (This block was written twice; the second copy could never run.)
            releasePanel(activity);
            SlothLog.note("back closed the browser's panel");
            return true;
        }
        if (drawer != null && drawer.getLayoutParams().height > 0) {
            animateTo(activity, 0);
            SlothLog.note("back closed the drawer");
            return true;
        }
        return false;
    }

    /** The widget chooser's state, for the harness. */
    static String sheetStateForTest() {
        return widgetSheet == null ? "null"
                : (widgetSheet.getVisibility() == View.VISIBLE ? "visible" : "gone")
                + " attached=" + (widgetSheet.getParent() != null)
                + " h=" + (widgetSheet.getParent() instanceof View
                ? ((View) widgetSheet.getParent()).getHeight() : -1);
    }

    /** The dashboard, for the harness. */
    static SlothBoard boardForTest() {
        return board;
    }

    /** The refresh the 800ms tick runs, for the harness. */
    static void refreshForTest(Activity activity) {
        refresh(activity);
    }

    /** The page's bottom padding as the shell set it, for the harness. */
    static int pagePadBottomForTest() {
        return appRootPadBottom;
    }

    /** The peek seat as measured, for the harness. */
    static int peekForTest(Activity activity) {
        return peekPx;
    }

    /** The status bar's height as the shell measured it, for the harness. */
    static int statusForTest() {
        return statusInset;
    }

    /** The last drag as a line of numbers, for the harness and for me. */
    static String dragTraceForTest() {
        return traceFrom + "->" + tracePeak + " moves=" + traceMoves + " v=" + traceVelocity
                + " seat=" + traceTo + " ms=" + traceMs;
    }

    /** How many times the page root has been re-laid-out, for the harness. */
    static int pageRelayoutsForTest() {
        return pageRelayouts;
    }

    /** Counts ticks so the sweeping work runs every few seconds, not on every beat. */
    private static int sweepTicks;
    /** The last leftover reported, so a box that keeps returning is named once per kind. */
    private static String leftoverReported = "";

    /**
     * Close what the app left open: its panel, its chrome, and the box around either.
     *
     * <p>The app opens its own panel for Bookmarks, History and Downloads, and draws
     * nothing into it now that those screens are ours. Collapsing the panel alone leaves
     * the card that held it - a bordered, rounded, completely empty box over the page,
     * which is exactly what "the bar is still there" looks like in a screenshot.</p>
     */
    static void sweepAppLeftovers(Activity activity) {
        boolean swept = false;
        if (!settingsOpen) {
            View panel = resolve(activity, "panel");
            if (panel != null && panel.getVisibility() == View.VISIBLE) {
                releasePanel(activity);
                forgetLeftover(activity, "panel");
                swept = true;
            }
            for (String name : CHROME) {
                View chrome = resolve(activity, name);
                if (chrome != null && chrome.getVisibility() != View.GONE) {
                    collapseView(chrome);
                    collapseLonelyAncestors(chrome);
                    forgetLeftover(activity, name);
                    swept = true;
                }
            }
        }
        if (swept) {
            checkLayers("swept the app's leftovers");
        }
    }

    private static void forgetLeftover(Activity activity, String what) {
        if (!what.equals(leftoverReported)) {
            leftoverReported = what;
            SlothLog.warn("closed the app's " + what + " again");
        }
    }

    /**
     * Collapse the wrappers that exist only to hold something we just collapsed.
     *
     * <p>Stops at the first ancestor that holds anything else, and never climbs past the
     * activity's content or the shell's own root, so this cannot take a page or the bar
     * with it.</p>
     */
    static void collapseLonelyAncestors(View view) {
        android.view.ViewParent parent = view == null ? null : view.getParent();
        for (int level = 0; level < 4 && parent instanceof View; level++) {
            View up = (View) parent;
            if (up == surfaceView || up == overlay || up instanceof android.widget.FrameLayout
                    && "shell-root".equals(String.valueOf(up.getTag()))) {
                return;
            }
            if (!(up instanceof ViewGroup)) {
                return;
            }
            ViewGroup group = (ViewGroup) up;
            if (group.getChildCount() != 1) {
                return;
            }
            if (up.getId() == android.R.id.content) {
                return;
            }
            collapseView(up);
            parent = up.getParent();
        }
    }

    /** Read-only view of the loan flag, for the harness. */
    static boolean settingsOpenForTest() {
        return settingsOpen;
    }

    /** Bring the diagnostics report up, rebuilt so it describes this moment, not an old one. */
    static void openDiagPage(final Activity activity) {
        if (settingsOpen) {
            releasePanel(activity);
        }
        closeDrawerFor(activity);
        View page = diagPage;
        ViewGroup content = (ViewGroup) activity.findViewById(android.R.id.content);
        View found = findTagged(content, "diag-page");
        if (found != null) {
            page = found;
            diagPage = found;
        }
        if (page == null) {
            SlothLog.error("no diagnostics page in this window");
            return;
        }
        // rebuilt on open: the report is a snapshot, and an old snapshot is a lie
        View fresh = SlothDiag.page(activity);
        if (page.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) page.getParent();
            int index = parent.indexOfChild(page);
            parent.removeViewAt(index);
            parent.addView(fresh, index, page.getLayoutParams());
        }
        diagPage = fresh;
        fresh.bringToFront();
        fresh.setVisibility(View.VISIBLE);
        SlothCover.probe(activity, "diagnostics opened");
    }

    /** The shell's own state, one line each: what the report says about the frame. */
    static String diagLines(Activity activity) {
        StringBuilder out = new StringBuilder();
        try {
            out.append("window: ").append(screenWidth(activity)).append("x")
                    .append(activity.getResources().getDisplayMetrics().heightPixels)
                    .append(" status=").append(statusInset)
                    .append(" nav=").append(navInset(activity)).append('\n');
            out.append("window mode: ").append(edgeFellBack ? "fitted (edge-to-edge reverted)"
                    : edgeToEdge ? "under the bars" : "fitted").append('\n');
            out.append("bar: ").append(barWrapView == null ? -1 : barWrapView.getHeight())
                    .append("px  surface: ").append(surfaceView == null ? -1
                            : surfaceView.getLayoutParams().height)
                    .append("px  drawer: ").append(drawer == null ? -1
                            : drawer.getLayoutParams().height).append("px\n");
            out.append("seats: peek=").append(peekPx).append(" expanded=").append(expandedPx)
                    .append(" full=").append(fullHeight(activity)).append('\n');
            out.append("state: peekShowing=").append(peekShowing)
                    .append(" settingsPage=").append(settingsOpen)
                    .append(" dockH=").append(dockH).append(" mainH=").append(mainH).append('\n');
            // what the last drag cost. A drag that writes the sheet sixty times and
            // re-lays-out the page sixty times is smooth only if the phone can afford it,
            // and this is the number that says which
            out.append("drag: ").append(dragWrites).append(" sheet writes, ")
                    .append(pageRelayouts).append(" page relayouts, since the app started\n");
            out.append("last drag: ").append(traceFrom < 0 ? "none yet"
                            : traceFrom + " -> " + tracePeak + "px in " + traceMoves
                            + " moves, " + traceMs + "ms, released at "
                            + Math.round(traceVelocity * 1000) + "px/s"
                            + (traceFlicked ? " (flick)" : " (slow)")
                            + ", settled at " + traceTo + "px")
                    .append('\n');
            View url = resolve(activity, "urlBar");
            out.append("url: ").append(url instanceof TextView
                    ? String.valueOf(((TextView) url).getText()) : "(no omnibox)").append('\n');
            out.append("tabs: ").append(tabCount(activity)).append('\n');
        } catch (Throwable t) {
            out.append("shell state failed: ").append(t.getClass().getSimpleName()).append('\n');
        }
        return out.toString();
    }

    /** Bring the shell's own settings page to the front, over the browser. */
    static void openSettingsPage(Activity activity) {
        if (settingsOpen) {
            // our page is about to cover the browser; its loaned panel goes away with it
            releasePanel(activity);
        }
        closeDrawerFor(activity);
        // ask this activity's window for its page rather than trusting a static that may
        // belong to a window that is gone - the page is in the tree it was built into
        View page = settingsPage;
        ViewGroup content = (ViewGroup) activity.findViewById(android.R.id.content);
        View found = findTagged(content, "settings-page");
        if (found != null) {
            page = found;
            settingsPage = found;
        }
        if (page == null) {
            SlothLog.note("no settings page in this window");
            return;
        }
        page.bringToFront();
        page.setVisibility(View.VISIBLE);
        checkLayers("opened settings");
    }

    /** Open find in page, over the top of the page and under the bar. */
    static void openFind(Activity activity) {
        if (findBar == null || findBar.getContext() != activity) {
            SlothLog.note("no find bar in this window");
            return;
        }
        closeDrawerFor(activity);
        findBar.bringToFront();
        findBar.setVisibility(View.VISIBLE);
        View field = findBar.findFocus() == null ? null : findBar.findFocus();
        View input = field;
        if (input == null) {
            input = ((ViewGroup) findBar).getChildAt(0) instanceof ViewGroup
                    ? ((ViewGroup) ((ViewGroup) findBar).getChildAt(0)).getChildAt(0) : null;
        }
        if (input != null) {
            input.requestFocus();
            Object ime = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (ime instanceof InputMethodManager) {
                ((InputMethodManager) ime).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        }
        SlothLog.note("find opened");
    }

    /** Close find and take the highlights with it. */
    static void closeFind(Activity activity) {
        if (findBar != null && findBar.getContext() == activity) {
            findBar.setVisibility(View.GONE);
            SlothFind.clear(activity);
            Object ime = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (ime instanceof InputMethodManager && activity.getCurrentFocus() != null) {
                ((InputMethodManager) ime).hideSoftInputFromWindow(
                        activity.getCurrentFocus().getWindowToken(), 0);
            }
            SlothLog.note("find closed");
        }
    }

    /** The address the browser is showing, for the library's save action. */
    static String currentUrl(Activity activity) {
        View real = resolve(activity, "urlBar");
        if (real instanceof EditText) {
            CharSequence text = ((EditText) real).getText();
            if (text != null && text.toString().trim().length() > 0) {
                return text.toString().trim();
            }
        }
        return null;
    }

    /** The title of the page on screen, if the app keeps one we can read. */
    static String currentTitle(Activity activity) {
        for (String name : new String[]{"pageTitle", "titleText", "tabTitle"}) {
            View view = resolve(activity, name);
            if (view instanceof TextView) {
                CharSequence text = ((TextView) view).getText();
                if (text != null && text.toString().trim().length() > 0) {
                    return text.toString().trim();
                }
            }
        }
        return null;
    }

    /**
     * Put the typed address into the app's own omnibox before navigating.
     *
     * <p>Returns the app's field when it can be found, so the browser's routine reads the
     * same text the app's widget shows. Falls back to the mirror when there is no real
     * omnibox to write to.</p>
     */
    static EditText syncOmnibox(Activity activity, EditText typed) {
        CharSequence text = typed.getText();
        View real = resolve(activity, "urlBar");
        if (!(real instanceof EditText) || real == typed) {
            return typed;
        }
        EditText app = (EditText) real;
        if (text != null && !text.toString().contentEquals(app.getText() == null ? "" : app.getText())) {
            app.setText(text);
            app.setSelection(app.getText().length());
        }
        return app;
    }

    /**
     * Put the settings page away without rebuilding or closing the drawer.
     *
     * <p>The page is a full-screen layer on the overlay: left visible it covers whatever
     * loads underneath and eats the taps meant for it. Anything that navigates has to
     * clear it first.</p>
     */
    static void hideSettingsPage(Activity activity) {
        View page = settingsPage;
        View found = findTagged((ViewGroup) activity.findViewById(android.R.id.content), "settings-page");
        if (found != null) {
            page = found;
        }
        if (page != null && page.getVisibility() == View.VISIBLE) {
            page.setVisibility(View.GONE);
        }
    }

    /** The first view under this root carrying a tag. */
    static View findTagged(View root, String tag) {
        if (root == null) {
            return null;
        }
        if (tag.equals(root.getTag())) {
            return root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                View found = findTagged(g.getChildAt(i), tag);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Show the dashboard: the menu that used to be the app's drawer is this now. */
    static void openDrawer(Activity activity) {
        if (drawer == null) {
            return;
        }
        hideSettingsPage(activity);
        if (drawer.getLayoutParams().height <= 0) {
            animateTo(activity, Math.max(dp(activity, 240), peekPx));
        }
    }

    static void openPalettePicker(Activity activity) {
        if (settingsPage != null) {
            settingsPage.setVisibility(View.GONE);
        }
        // the list lives in the drawer, and a drawer that is shut has no room for it
        ensureDrawerOpen(activity);
        if (paletteSheet != null) {
            paletteSheet.bringToFront();
            paletteSheet.setVisibility(View.VISIBLE);
            checkLayers("opened the palette list");
        }
    }

    static void openWidgetChooser(Activity activity) {
        ensureDrawerOpen(activity);
        if (widgetSheet != null) {
            widgetSheet.bringToFront();
            widgetSheet.setVisibility(View.VISIBLE);
            checkLayers("opened the widget list");
        }
    }

    /**
     * Keep the app's panel shut.
     *
     * <p>It was lent to the app so its own screens had somewhere to draw. Nothing draws
     * there now that the settings page is ours, and a lent panel is an empty box over the
     * page - visible, bordered, with nothing in it.</p>
     */
    static void releasePanel(Activity activity) {
        boolean wasOpen = settingsOpen;
        settingsOpen = false;
        for (String name : PANEL) {
            View v = resolve(activity, name);
            if (v != null) {
                collapseView(v);
                collapseLonelyAncestors(v);
            }
        }
        if (wasOpen) {
            // the app collapses its page while a panel is up; without this the page stays
            // zero-height and the window is black with an address in it
            closeAppPanel(activity);
        }
    }

    private static boolean stop(Activity activity) {
        try {
            java.lang.reflect.Method m = Class.forName("com.sloth.browser.BarUnion")
                    .getDeclaredMethod("stop", Activity.class);
            m.setAccessible(true);
            m.invoke(null, activity);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * A sideways flick across the bar moves to the next or previous tab.
     *
     * <p>The bar is the widest, always-present thing in this browser and it already owns the
     * vertical gesture, so it is where a thumb arrives for a tab change. It presses the
     * browser's own tab tile rather than re-implementing a switch, and it stops at the ends
     * instead of wrapping: a flick that lands you on the tab you were trying to leave is
     * worse than one that does nothing.</p>
     */
    static boolean swipeTab(Activity activity, boolean forward) {
        ViewGroup chips = resolveStrip(activity);
        if (chips == null || chips.getChildCount() < 2) {
            return false;
        }
        int current = -1;
        for (int i = 0; i < chips.getChildCount(); i++) {
            View chip = chips.getChildAt(i);
            if (chip.isSelected() || chip.isActivated()) {
                current = i;
                break;
            }
        }
        if (current < 0) {
            return false;
        }
        int target = current + (forward ? 1 : -1);
        if (target < 0 || target >= chips.getChildCount()) {
            return false;
        }
        chips.getChildAt(target).performClick();
        SlothLog.note("swiped to tab " + (target + 1) + " of " + chips.getChildCount());
        return true;
    }

    /** Whether the field holds something with a host in it, rather than a search phrase. */
    static boolean looksLikeAddress(String text) {
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

    /** Deep surface, hairline edge, enough contrast to read against a page. */

    private static View addressPill(final Activity activity) {
        LinearLayout pill = new LinearLayout(activity);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(activity, 12), 0, dp(activity, 6), 0);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{SlothTheme.lineAt(0x0E), SlothTheme.field()});
        bg.setCornerRadius(dp(activity, 20));
        bg.setStroke(Math.max(1, dp(activity, 1)), SlothTheme.lineAt(0x2E));
        pill.setBackground(bg);
        pill.setLayoutParams(new LinearLayout.LayoutParams(0,
                dp(activity, 40), 1f));
        // the bar steps back as the sheet arrives; the field used to stay at full weight
        // and hang there like a lamp left on
        addressPillView = pill;
        final GradientDrawable pillBg = bg;

        final SlothIcons lock = SlothIcons.of(activity, SlothIcons.Kind.SEARCH, 15,
                SlothTheme.muted());
        lock.setContentDescription("Site settings");
        lock.setClickable(true);
        lock.setFocusable(true);
        lock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tap(activity, "pageInfo");
            }
        });
        LinearLayout.LayoutParams lockLp = new LinearLayout.LayoutParams(
                dp(activity, 26), dp(activity, 34));
        pill.addView(lock, lockLp);

        final EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        input.setTextColor(INK);
        input.setHintTextColor(DIM);
        input.setHint("Search or open");
        input.setContentDescription("Search or open");
        input.setBackground(null);
        input.setPadding(0, 0, 0, 0);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setImeOptions(EditorInfo.IME_ACTION_GO);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        inputLp.leftMargin = dp(activity, 8);
        input.setLayoutParams(inputLp);
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                String text = input.getText() == null ? "" : input.getText().toString().trim();
                if (text.length() == 0) {
                    return false;
                }
                input.clearFocus();
                try {
                    Object ime = activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (ime instanceof InputMethodManager) {
                        ((InputMethodManager) ime).hideSoftInputFromWindow(input.getWindowToken(), 0);
                    }
                } catch (Throwable ignored) {
                }
                // DrawerFace.go is the app's own navigation routine: it trims the text,
                // hides the keyboard and decides between a URL and a search. Calling the
                // activity's "go" directly skipped all of that.
                if (!navigate(activity, input)) {
                    // last resort: whichever "go" the activity exposes, private or not
                    try {
                        call(activity, "go", new Class<?>[]{String.class}, new Object[]{text});
                    } catch (Throwable ignored) {
                    }
                }
                return true;
            }
        });
        // a browser selects the whole address when you touch it
        input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    input.selectAll();
                }
                pillBg.setStroke(Math.max(1, dp(activity, 1)),
                        hasFocus ? SlothTheme.lineAt(0x8C) : SlothTheme.lineAt(0x2E));
                if (clearTag != null) {
                    clearTag.setVisibility(hasFocus && input.getText().length() > 0
                            ? View.VISIBLE : View.GONE);
                }
            }
        });
        pill.addView(input);
        address = input;
        // a fresh tab has no address, so no padlock: the watcher only fires on a change,
        // and "empty" to "empty" is not one
        lock.setClickable(false);
        lock.setFocusable(false);
        lock.setContentDescription("Search");
        input.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                CharSequence value = input.getText();
                if (value == null || value.length() == 0) {
                    return false;
                }
                try {
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Address", value));
                        Toast.makeText(activity, "Address copied", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                } catch (Throwable ignored) {
                }
                return false;
            }
        });
        pill.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private boolean swiped;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getX();
                        downY = e.getY();
                        swiped = false;
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        if (!input.hasFocus()) {
                            float dx = e.getX() - downX;
                            float dy = e.getY() - downY;
                            if (!swiped && Math.abs(dx) > dp(activity, 56)
                                    && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                                swiped = true;
                                switchTab(activity, dx < 0 ? 1 : -1);
                                return true;
                            }
                        }
                        return false;
                    default:
                        return false;
                }
            }
        });

        final TextView clear = new TextView(activity);
        clear.setText("\u00D7");
        clear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        clear.setTextColor(DIM);
        clear.setGravity(Gravity.CENTER);
        clear.setContentDescription("Clear the address");
        clear.setLayoutParams(new LinearLayout.LayoutParams(dp(activity, 28), dp(activity, 28)));
        clear.setVisibility(View.GONE);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                input.setText("");
                input.requestFocus();
            }
        });
        pill.addView(clear);
        clearTag = clear;

        input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
                if (clearTag != null) {
                    clearTag.setVisibility(input.hasFocus() && s.length() > 0 ? View.VISIBLE : View.GONE);
                }
                if (lock != null) {
                    boolean ok = secure(s.toString());
                    // on a new tab the field is a search box: a padlock there claims a
                    // security state for a page that does not exist yet, so the slot holds
                    // a magnifier until there is an address to be secure about
                    boolean address = looksLikeAddress(s.toString());
                    lock.kind(address ? SlothIcons.Kind.LOCK : SlothIcons.Kind.SEARCH);
                    lock.setClickable(address);
                    lock.setFocusable(address);
                    if (!address) {
                        lock.tint(SlothTheme.muted());
                        lock.setContentDescription("Search");
                        return;
                    }
                    // the lock is a lock; an insecure address is said, not painted - a
                    // second symbol in the same place would just look like a broken lock.
                    // The colour is the warning: ink when the address is secure, amber when
                    // it is not, and the description says which
                    lock.tint(ok ? SlothTheme.dim() : 0xFFE2A05A);
                    lock.setContentDescription(ok
                            ? "Site settings, secure connection"
                            : "Site settings, not a secure address");
                }
            }

            public void afterTextChanged(android.text.Editable s) {
            }
        });
        return pill;
    }
/** The padding that puts the page's top edge at the bar's bottom edge. */
    static int pagePaddingFor(int appRootTop, int surfaceBottom) {
        // the surface already includes the drawer: adding its height again pushed the page
        // a whole drawer further down than the bar
        return Math.max(0, surfaceBottom - appRootTop);
    }

    static void alignPageToBar(Activity activity, int drawerHeight) {
        alignPageToBar(activity, drawerHeight, dragging || animating);
    }

    /**
     * Put the page under the bar.
     *
     * <p>While the sheet is moving, the page is <em>translated</em> rather than padded: a
     * padding write is a layout pass, and the app's page root holds the whole browser
     * (GeckoView included), so padding it every frame resized the engine surface on every
     * frame of every drag - the single most expensive thing a drag did. A translation is a
     * compositor move: the surface keeps its size and follows the sheet. On release the real
     * padding is committed once and the translation returns to zero, so rest is exact and
     * nothing drifts.</p>
     */
    static void alignPageToBar(Activity activity, int drawerHeight, boolean live) {
        if (appRoot == null || surfaceView == null || appRootPadTop < 0) {
            return;
        }
        int[] surfaceAt = new int[2];
        int[] rootAt = new int[2];
        surfaceView.getLocationOnScreen(surfaceAt);
        appRoot.getLocationOnScreen(rootAt);
        int surfaceBottom = surfaceAt[1] + surfaceView.getHeight();
        if (surfaceView.getHeight() <= 0 || appRoot.getTop() < 0) {
            // not laid out yet: the bar's own height is the best figure available, and the
            // measured one replaces it on the next frame
            int estimate = surfaceBarPx + Math.max(0, drawerHeight);
            if (live) {
                float shift = estimate - appRoot.getPaddingTop();
                if (Math.abs(appRoot.getTranslationY() - shift) > 0.5f) {
                    appRoot.setTranslationY(shift);
                }
                return;
            }
            if (appRoot.getPaddingTop() != estimate) {
                appRoot.setPadding(appRoot.getPaddingLeft(), estimate,
                        appRoot.getPaddingRight(), appRoot.getPaddingBottom());
            }
            if (appRoot.getTranslationY() != 0f) {
                appRoot.setTranslationY(0f);
            }
            return;
        }
        // getLocationOnScreen walks the hierarchy applying each view's matrix, so rootAt[1]
        // already contains the translationY this method sets. Feeding it back makes the live
        // branch self-referential: the page converges on half the travel and the commit leaves
        // it short. Undo it once, here.
        int rootTop = rootAt[1] + Math.round(appRoot.getTranslationY());
        int top = pagePaddingFor(rootTop, surfaceBottom);
        int committed = appRoot.getPaddingTop();
        if (live) {
            // the page follows the sheet without being measured
            float shift = top - committed;
            if (Math.abs(appRoot.getTranslationY() - shift) > 0.5f) {
                appRoot.setTranslationY(shift);
            }
            return;
        }
        if (committed != top) {
            appRoot.setPadding(appRoot.getPaddingLeft(), top,
                    appRoot.getPaddingRight(), appRoot.getPaddingBottom());
            pageRelayouts++;
        }
        if (appRoot.getTranslationY() != 0f) {
            appRoot.setTranslationY(0f);
        }
    }

    /** https and about: are the quiet ones; plain text is not a secure origin. */
    private static boolean secure(String url) {
        String u = url == null ? "" : url.trim().toLowerCase(java.util.Locale.UK);
        return u.startsWith("https://") || u.startsWith("about:") || u.length() == 0;
    }

    /**
     * A bar control: drawn, wired to the app's own button, and fading with the chrome.
     *
     * <p>The glyph text is gone from the bar. A chevron and a plus from a text font share
     * neither stroke width nor optical centre, so the row read as characters sitting next
     * to a field rather than as controls.</p>
     */
    private static View barIcon(final Activity activity, SlothIcons.Kind kind, String label,
                                final String target, int sizeDp) {
        SlothIcons view = SlothIcons.of(activity, kind, sizeDp, INK);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) view.getLayoutParams();
        if (lp != null) {
            // the same breathing room either side of every control, so the row is even
            lp.leftMargin = dp(activity, 2);
            lp.rightMargin = dp(activity, 2);
        }
        view.setContentDescription(label);
        view.setBackground(pressState(activity));
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tap(activity, target);
            }
        });
        chromeBase.put(view, 1f);
        return view;
    }

        /** A round press state without pulling in a drawable resource. */
    private static android.graphics.drawable.Drawable pressState(Activity activity) {
        android.graphics.drawable.StateListDrawable states =
                new android.graphics.drawable.StateListDrawable();
        GradientDrawable pressed = new GradientDrawable();
        pressed.setCornerRadius(dp(activity, 22));
        pressed.setColor(0x22EEF7F1);
        GradientDrawable idle = new GradientDrawable();
        idle.setCornerRadius(dp(activity, 22));
        idle.setColor(0);
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{}, idle);
        return states;
    }

    /* --------------------------------------------------------------- drawer ops */
    /**
     * The bar and the drawer are one panel. Closed, the bar is a capsule floating on the
     * page. Open, its bottom corners open out and its side insets close in, the drawer's
     * top corners are square, and the two share a fill - so the seam disappears instead
     * of reading as two stacked boxes.
     */
    private static void applyUnion(Activity activity, boolean open) {
        if (barView == null) {
            return;
        }
        if (open == unionOpen) {
            // the drag calls this on every frame, and the open branch allocates four
            // drawables: the shape only changes when the state does
            return;
        }
        unionOpen = open;
        // the bar is transparent and the drawer is transparent: there is one fill, on the
        // surface, so opening the dashboard cannot expose a join between two panels
        barView.setBackground(null);
        if (drawer != null) {
            drawer.setBackground(null);
        }
    }


    /** The panel's own outline: a pill closed, a single rounded slab open. */
    private static void panelShape(Activity activity, boolean open) {
        if (surfaceView == null) {
            return;
        }
        int r = dp(activity, 28);
        int bottom = dp(activity, SlothTheme.radius());
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(SlothTheme.surface());
        shape.setCornerRadii(open
                ? new float[]{r, r, r, r, bottom, bottom, bottom, bottom}
                : new float[]{r, r, r, r, r, r, r, r});
        surfaceView.setBackground(shape);
    }

    private static void setDrawerHeight(Activity activity, int h) {
        int was = drawer == null || drawer.getLayoutParams() == null
                ? 0 : drawer.getLayoutParams().height;
        if (h == 0) {
            // the pickers live in the drawer; a shut drawer must not leave them showing
            if (paletteSheet != null && paletteSheet.getVisibility() == View.VISIBLE) {
                paletteSheet.setVisibility(View.GONE);
            }
            if (widgetSheet != null && widgetSheet.getVisibility() == View.VISIBLE) {
                widgetSheet.setVisibility(View.GONE);
            }
            // only on the way shut: this walks the tree and reads the log, and a drag held
            // at zero called it on every frame
            if (was > 0) {
                checkLayers("closed the drawer");
            }
        }
        if (h > 0 && settingsOpen) {
            // pulling the drawer down means the errand moved on: the loan is over
            releasePanel(activity);
        }
        if (h > 0 && settingsPage != null && settingsPage.getVisibility() == View.VISIBLE) {
            // pulling the drawer down means the errand moved on
            settingsPage.setVisibility(View.GONE);
        }
        // not at the peek: there the sheet is deliberately shorter than the board it hides,
        // and capping it against that content would stretch it back into empty paper
        if (h > 0 && measuredContentH > 0 && !peekShowing && !dragging) {
            // a sheet does not need to be taller than what it holds - the light render
            // showed the drawer stretched past its content into a band of empty paper
            // a little air under the last row, not a band: the panel must not open taller
            // than what it holds or the extra reads as an empty strip
            int contentCap = measuredContentH + dp(activity, 10);
            if (contentCap < h) {
                h = Math.max(dp(activity, 200), contentCap);
            }
        }
        applyUnion(activity, h > dp(activity, 6));
        dragWrites++;
        if (barWrapView == null) {
            return;
        }
        ViewGroup.LayoutParams lp = drawer.getLayoutParams();
        lp.height = h;
        drawer.setLayoutParams(lp);
        drawer.setVisibility(h > 0 ? View.VISIBLE : View.GONE);
        // the surface follows the drawer exactly; a wrapping surface measures the painted
        // backdrop against the whole window and covers the page with the scene
        applySurfaceHeight(activity, h);
        if (board != null) {
            // at the peek the sheet is a strip - close, the open tabs, and nothing else.
            // Two thresholds, not one: with a single line the sheet flipped between strip
            // and dashboard every few pixels of a slow drag, and the drawer appeared to
            // stutter and fight the finger at exactly the height most drags pass through
            int enterAt = peekPx + dp(activity, 8);
            int leaveAt = peekPx + dp(activity, 44);
            boolean peek = h > 0 && (peekShowing ? h <= leaveAt : h <= enterAt);
            showPeek(activity, peek);
        }
        // and the scene slides as the sheet comes out, so the sheet is in front of it
        if (backdropView != null) {
            backdropView.setDepth(h / (float) Math.max(1, expandedPx));
        }

        if (was == 0 && h > 0 && board != null) {
            // the cards arrive in sequence rather than all at once
            board.reveal();
        }
        if (was == 0 && h > 0 && scroller != null) {
            // the drawer always appears at its top: tabs first, widgets below
            scroller.scrollTo(0, 0);
        }
        if (appRoot != null) {
            // the page dims as the bar grows, instead of a scrim appearing over it
            float cap = Math.max(1f, peekPx * 1.2f);
            float fraction = Math.min(1f, h / cap);
            appRoot.setAlpha(1f - 0.22f * fraction);
        }
        // One height drives both, but the bar does not vanish under the finger: it holds
        // its weight through the first half of the drag, then steps back. The drawer's
        // content fades up with it, so a half-open drawer reads as part of the bar
        // rather than as a separate sheet with a footer floating in it.
        float span = Math.max(1f, peekPx);
        float fraction = Math.min(1f, h / span);
        // the bar does not fade out when the dashboard opens: it is the dashboard's header.
        // Dimming it to a fifth said "this is a different surface underneath".
        chromeFade = 1f;
        applyChromeFade();
        if (footRow != null) {
            // the peek shows tabs and actions only; the footer waits for a real open
            int roomForFooter = Math.round(Math.max(peekPx * 1.6f, expandedHeight(activity) * 0.55f));
            int wantedFooter = h >= roomForFooter ? View.VISIBLE : View.GONE;
            if (footRow.getVisibility() != wantedFooter) {
                footRow.setVisibility(wantedFooter);
            }
        }
        if (drawer instanceof ViewGroup && ((ViewGroup) drawer).getChildCount() > 0) {
            ((ViewGroup) drawer).getChildAt(0).setAlpha(Math.min(1f, 0.35f + fraction));
        }
        if (board != null) {
            boolean atPeek = h > 0 && h <= peekPx + dp(activity, 24);
            if (atPeek != boardCompact) {
                boardCompact = atPeek;
                board.setCompact(atPeek);
            }
        }
        if (appRoot != null && appRootPadTop >= 0) {
            // push, not overlay: the page starts where our bar ends, and moves down by
            // however much drawer is showing
            alignPageToBar(activity, h);
        }
    }

    /** Turning animations off in system settings means no animation here either. */
    private static boolean motionOff(Activity activity) {
        try {
            float scale = android.provider.Settings.Global.getFloat(activity.getContentResolver(),
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            return scale == 0f;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Frame-counted, not clock-counted: a wall-clock animation depends on how
     * fast frames arrive, which makes it untestable and jittery on a slow phone.
     */
    private static void animateTo(final Activity activity, final int target) {
        if (drawer == null) {
            SlothLog.warn("animated without a drawer");
            return;
        }
        final int from = drawer.getLayoutParams().height;
        openIntent = target > 0;
        if (openIntent && from < dp(activity, 8) && scroller != null) {
            scroller.post(new Runnable() {
                @Override
                public void run() {
                    scroller.scrollTo(0, 0);
                }
            });
        }
        if (motionOff(activity) || from == target) {
            drawer.setAlpha(1f);
            drawer.setTranslationY(0);
            setDrawerHeight(activity, target);
            return;
        }
        drawer.setVisibility(View.VISIBLE);
        drawer.setAlpha(1f);
        drawer.setTranslationY(0);
        // A 500dp drop and a 40dp nudge cannot take the same time, and a frame count
        // cannot either: at 16 ms a frame the snap stretched whenever the phone dropped
        // one, which read as the settle stuttering. The duration is in milliseconds and
        // the progress is read from the clock, so a slow frame skips ahead instead of
        // stretching the animation.
        int distance = Math.abs(target - from);
        final int duration = Math.max(120, Math.min(260,
                Math.round(120 + distance * 0.35f)));
        final int delta = target - from;
        final boolean opening = from < dp(activity, 8);
        if (opening) {
            // content settles in behind the sheet rather than snapping into place
            drawer.setAlpha(0.85f);
            drawer.setTranslationY(dp(activity, 10));
        }
        final int mine = gen;
        animating = true;
        final long started = android.os.SystemClock.uptimeMillis();
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                if (mine != gen) {
                    // the shell moved on mid-flight: leave nothing half-transparent behind
                    if (drawer != null) {
                        drawer.setAlpha(1f);
                        drawer.setTranslationY(0);
                    }
                    if (appRoot != null) {
                        appRoot.setAlpha(1f);
                    }
                    animating = false;
                    return;
                }
                long now = android.os.SystemClock.uptimeMillis();
                float t = Math.min(1f, (now - started) / (float) duration);
                float eased = 1f - (1f - t) * (1f - t);
                setDrawerHeight(activity, from + Math.round(delta * eased));
                if (opening) {
                    drawer.setAlpha(0.85f + 0.15f * eased);
                    drawer.setTranslationY(dp(activity, Math.round(10 * (1f - eased))));
                }
                if (t < 1f) {
                    MAIN.postDelayed(this, 16);
                } else {
                    animating = false;
                    // the settle's last word: the page takes its exact offset and the
                    // translation that followed the sheet is cleared in one layout
                    if (appRootPadTop >= 0) {
                        alignPageToBar(activity, target, false);
                    }
                }
            }
        });
    }

    /**
     * The app still binds a pull gesture to a view tagged "sloth-pull" that opens
     * its own drawer. With that drawer gone the gesture opens nothing and eats the
     * swipe; DrawerFace.free does exactly this cleanup, and doing it here means a
     * re-claim on the next pass is undone too.
     */
    private static void freeLegacyPull(Activity activity) {
        try {
            ViewGroup content = (ViewGroup) activity.findViewById(Window.ID_ANDROID_CONTENT);
            freePullIn(content);
        } catch (Throwable ignored) {
        }
    }

    private static void freePullIn(View view) {
        if (view == null) {
            return;
        }
        Object tag = view.getTag();
        if (tag != null && "sloth-pull".equals(tag.toString())) {
            view.setOnTouchListener(null);
            view.setTag(null);
            view.setClickable(false);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                freePullIn(group.getChildAt(i));
            }
        }
    }

    /** Settings is done with the panel; put the old surface away again. */
    private static void leaveSettings(Activity activity) {
        if (!settingsOpen) {
            return;
        }
        settingsOpen = false;
        for (String name : new String[]{"panel", "panelBody"}) {
            View v = resolve(activity, name);
            if (v != null) {
                collapseView(v);
            }
        }
    }
/**
     * Fill the peek from the browser's own tab strip: a close control, one square per open
     * tab, and a way to open another.
     *
     * <p>Titles belong on the full sheet. At forty-odd dp of height a square carrying the
     * page's initial and the tab's accent is readable, and the row scrolls sideways instead
     * of shrinking titles to nothing.</p>
     */
    private static void fillPeek(Activity activity) {
        if (peekRow == null) {
            return;
        }
        // the tick asks every 800 ms; rebuilding identical tiles that often would throw
        // away the row's scroll position and burn frames for nothing
        String signature = peekSignature(activity);
        if (signature.equals(lastPeekSignature) && peekRow.getChildCount() > 0) {
            return;
        }
        lastPeekSignature = signature;
        peekRow.removeAllViews();

        View close = peekButton(activity, "peek-close", SlothIcons.Kind.CLOSE, "Close the drawer",
                SlothTheme.ink(), CARD);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                animateTo(activity, 0);
            }
        });
        peekRow.addView(close);

        ViewGroup chips = resolveStrip(activity);
        int active = -1;
        if (chips != null) {
            for (int i = 0; i < chips.getChildCount(); i++) {
                if (chips.getChildAt(i).isSelected() || chips.getChildAt(i).isActivated()) {
                    active = i;
                    break;
                }
            }
        }
        if (chips != null) {
            for (int i = 0; i < chips.getChildCount(); i++) {
                final View chip = chips.getChildAt(i);
                final CharSequence title = label(chip);
                final boolean current = i == active;
                final View tile = peekTile(activity, title, current);
                tile.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        chip.performClick();
                        animateTo(activity, 0);
                    }
                });
                // a square is small enough that a second gesture belongs on it: holding one
                // closes that tab, which is the thing you want after a flick through them
                tile.setLongClickable(true);
                tile.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        View close = byTagDeep(chip, "close");
                        if (close == null) {
                            SlothLog.warn("this browser offers no close on a tab");
                            return false;
                        }
                        close.performClick();
                        SlothLog.note("closed a tab from the peek");
                        fillPeek(activity);
                        return true;
                    }
                });
                peekRow.addView(tile);
            }
        }

        View plus = peekButton(activity, "peek-new", SlothIcons.Kind.PLUS, "New tab",
                SlothTheme.dim(), SlothTheme.lineAt(0x12));
        plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tap(activity, "btnPlus");
                animateTo(activity, 0);
            }
        });
        peekRow.addView(plus);
        // the squares are added after this build, so the drag is attached last: the strip
        // has to react to a finger that lands on a tab, not only on the gaps between them
        attachStripDrag(peekRow);
    }

    /** Put the strip's drag on a view and everything under it. */
    private static void attachStripDrag(View view) {
        if (view == null || stripDrag == null) {
            return;
        }
        view.setOnTouchListener(stripDrag);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                attachStripDrag(group.getChildAt(i));
            }
        }
    }

    /**
     * Dim a chevron the browser has run out of history for.
     *
     * <p>The app keeps its own back and forward buttons and knows when they are dead; ours
     * were always drawn at full ink, so the bar said "you can go forward" on a page you had
     * just opened. The app is asked rather than guessed at.</p>
     */
    static void syncHistory(Activity activity) {
        View backBtn = resolve(activity, "btnBack");
        View fwdBtn = resolve(activity, "btnFwd");
        if (backGlyph instanceof SlothIcons) {
            ((SlothIcons) backGlyph).tint(backBtn != null && !backBtn.isEnabled()
                    ? SlothTheme.muted() : INK);
        }
        if (fwdGlyph instanceof SlothIcons) {
            ((SlothIcons) fwdGlyph).tint(fwdBtn != null && !fwdBtn.isEnabled()
                    ? SlothTheme.muted() : INK);
        }
    }

    /** What the strip should be showing: the tabs' names, and which one is current. */
    private static String peekSignature(Activity activity) {
        StringBuilder out = new StringBuilder();
        ViewGroup chips = resolveStrip(activity);
        if (chips == null) {
            return "";
        }
        for (int i = 0; i < chips.getChildCount(); i++) {
            View chip = chips.getChildAt(i);
            out.append(label(chip)).append(chip.isSelected() || chip.isActivated() ? "*" : "")
                    .append('|');
        }
        return out.toString();
    }

    private static View peekButton(Activity activity, String tag, SlothIcons.Kind kind,
                                   String label, int colour, int fill) {
        LinearLayout box = new LinearLayout(activity);
        box.setGravity(Gravity.CENTER);
        box.setBackground(round(activity, 999, fill, SlothTheme.hairline(0x55)));
        box.setTag(tag);
        box.setContentDescription(label);
        box.setClickable(true);
        box.setFocusable(true);
        box.addView(SlothIcons.of(activity, kind, 16, colour));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                dp(activity, 38), dp(activity, 38));
        lp.rightMargin = dp(activity, 8);
        box.setLayoutParams(lp);
        return box;
    }

    private static View peekTile(Activity activity, CharSequence title, boolean current) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(round(activity, 12,
                current ? SlothTheme.lineAt(0x2E) : CARD,
                current ? SlothTheme.accent() : SlothTheme.hairline(0x33)));
        TextView initial = new TextView(activity);
        String text = title == null ? "" : title.toString().trim();
        initial.setText(text.isEmpty() ? "?" : text.substring(0, 1).toUpperCase(Locale.UK));
        initial.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        initial.setTextColor(current ? SlothTheme.ink() : SlothTheme.dim());
        initial.setTypeface(initial.getTypeface(), android.graphics.Typeface.BOLD);
        tile.addView(initial);
        tile.setTag("peek-tab");
        tile.setContentDescription((current ? title + ", current tab" : "Switch to " + title)
                + ", long press to close");
        tile.setClickable(true);
        tile.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                dp(activity, 38), dp(activity, 38));
        lp.rightMargin = dp(activity, 6);
        tile.setLayoutParams(lp);
        return tile;
    }

    /** The peek shows the strip instead of the board; it is one sheet, two faces. */
    private static void showPeek(Activity activity, boolean peek) {
        if (peekScroll == null) {
            return;
        }
        if (peek == peekShowing) {
            // the drag calls this on every frame; rebuilding the strip, or flipping the
            // scroller's visibility, mid-gesture is what made the drawer stutter
            return;
        }
        if (peek) {
            fillPeek(activity);
        }
        peekScroll.setVisibility(peek ? View.VISIBLE : View.GONE);
        if (!peek) {
            lastPeekSignature = "";
        }
        if (scroller != null) {
            scroller.setVisibility(peek ? View.GONE : View.VISIBLE);
        }
        if (drawer != null) {
            // at the peek the sheet is at the top of the screen, so the navigation bar's
            // strip of padding would be empty paper inside it
            drawer.setPadding(0, 0, 0, peek ? 0 : navInset(activity));
        }
        peekShowing = peek;
    }

    /**
     * The first open size: the strip the peek shows, measured once from its own row.
     *
     * <p>It was a fixed 230 dp of dashboard; at that height a strip sitting on top of a band
     * of empty paper read as a sheet that had failed to load.</p>
     */
    private static void refinePeek(Activity activity) {
        if (board == null || peekFromContent) {
            return;
        }
        int width = screenWidth(activity);
        int fitted = board.measurePeek(width);
        if (fitted <= 0) {
            return;
        }
        int cap = Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.55f);
        // the peek is a strip now, not a small dashboard: close, the tabs, a new tab, and
        // the grip above them. Sized to that, because a sheet that shows a row and then a
        // band of empty paper looks like it failed to load
        int strip = dp(activity, 72);
        peekPx = Math.min(cap, strip);
        peekFromContent = true;
    }

    /**
     * The height of a fully open sheet: its content plus a little air, never past 85% of
     * the screen. Cached while the peek is showing, because a peeking board measures as a
     * strip and the seats would collapse to nothing.
     */
    private static int expandedHeight(Activity activity) {
        int cap = Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.85f);
        // a peeking board has its sections hidden, so its measured height would say the sheet
        // is strip-sized and the seats would collapse to nothing; the fitted height is cached
        int content = board == null || peekShowing ? 0 : board.view().getMeasuredHeight();
        if (content > 0 && content != measuredContentH) {
            measuredContentH = content;
            expandedPx = Math.min(cap, content + dp(activity, 24));
        }
        if (expandedPx <= 0) {
            // a fallback that is still a sheet: the strip alone would be a sliver
            expandedPx = Math.min(cap, Math.max(Math.round(peekPx * 1.85f),
                    Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.55f)));
        }
        return Math.min(expandedPx, cap);
    }

    private static void rememberOpenSize(Activity activity, boolean expanded) {
        try {
            activity.getSharedPreferences("slothworks", Context.MODE_PRIVATE)
                    .edit().putBoolean("drawerExpanded", expanded).apply();
        } catch (Throwable ignored) {
        }
    }

    /**
     * The tallest the drawer can be: the whole surface below the status bar and above
     * the navigation bar. This is what a drag may reach, and the seat it lands on when
     * thrown all the way down.
     */
    private static int fullHeight(Activity activity) {
        int screen = activity.getResources().getDisplayMetrics().heightPixels;
        return Math.max(dp(activity, 240), screen - statusBarHeight(activity) - navInset(activity));
    }

    /** Open at the size it was left at, not always at the smallest one. */
    private static int openHeight(Activity activity) {
        try {
            boolean expanded = activity.getSharedPreferences("slothworks", Context.MODE_PRIVATE)
                    .getBoolean("drawerExpanded", false);
            return expanded ? expandedHeight(activity) : peekPx;
        } catch (Throwable ignored) {
            return peekPx;
        }
    }

    /**
     * Where a released drag lands. The finger's own speed decides first - a flick
     * should go where it was thrown - and a slow release goes to the nearest of the
     * three resting sizes, projected forward a little so it does not feel sticky.
     */
    private static void finishDrag(Activity activity, float velocity) {
        if (drawer == null) {
            return;
        }
        int h = drawer.getLayoutParams().height;
        int peek = peekPx;
        int expanded = expandedHeight(activity);
        int full = fullHeight(activity);

        int[] seats = new int[]{0, peek, Math.max(expanded, peek + dp(activity, 40)), full};
        int target = SlothSeats.choose(seats, h, velocity, peek, dp(activity, 8));

        // a seat change is worth a tick under the finger: the drag already ends where the
        // person meant, but without it the sheet appears to stop somewhere on its own
        if (target != lastSeat) {
            lastSeat = target;
            try {
                drawer.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            } catch (Throwable ignored) {
                // a device without haptics is not a fault
            }
        }
        traceTo = target;
        animateTo(activity, target);
        rememberOpenSize(activity, target > 0);
    }

    private static void collapse(Activity activity, String bindingName) {
        View v = bound(activity, bindingName);
        if (v == null) {
            return;
        }
        if (!legacyState.containsKey(bindingName)) {
            ViewGroup.LayoutParams slp = v.getLayoutParams();
            int h = slp == null ? ViewGroup.LayoutParams.WRAP_CONTENT : slp.height;
            int top = 0, bottom = 0;
            if (slp instanceof ViewGroup.MarginLayoutParams) {
                top = ((ViewGroup.MarginLayoutParams) slp).topMargin;
                bottom = ((ViewGroup.MarginLayoutParams) slp).bottomMargin;
            }
            legacyState.put(bindingName, new int[]{h, top, bottom,
                    v.getPaddingLeft(), v.getPaddingRight(), v.getVisibility()});
        }
        if ("chromeDock".equals(bindingName)) {
            dock = v;
            ViewGroup.LayoutParams dlp = v.getLayoutParams();
            if (dlp != null && dockH < 0) {
                dockH = dlp.height;
                if (dlp instanceof ViewGroup.MarginLayoutParams) {
                    dockTop = ((ViewGroup.MarginLayoutParams) dlp).topMargin;
                    dockBottom = ((ViewGroup.MarginLayoutParams) dlp).bottomMargin;
                }
                dockPadL = v.getPaddingLeft();
                dockPadR = v.getPaddingRight();
            }
        }
        if ("chromeMain".equals(bindingName)) {
            main = v;
            ViewGroup.LayoutParams mlp = v.getLayoutParams();
            if (mlp != null && mainH == 0) {
                mainH = mlp.height;
            }
        }
        v.setVisibility(View.GONE);
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp != null) {
            lp.height = 0;
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) lp).topMargin = 0;
                ((ViewGroup.MarginLayoutParams) lp).bottomMargin = 0;
            }
            v.setLayoutParams(lp);
        }
        if (v instanceof ViewGroup) {
            ((ViewGroup) v).setPadding(0, 0, 0, 0);
        }
    }

    /** Put the browser's own bar back, or take it away again. */


    private static void collapseView(View v) {
        v.setVisibility(View.GONE);
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp != null) {
            lp.height = 0;
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) lp).topMargin = 0;
                ((ViewGroup.MarginLayoutParams) lp).bottomMargin = 0;
            }
            v.setLayoutParams(lp);
        }
        if (v instanceof ViewGroup) {
            ((ViewGroup) v).setPadding(0, 0, 0, 0);
        }
    }

    /** Applied whenever the window reports its insets, which is after the shell is built. */
    static void applyBottomInset(Activity activity, int inset) {
        if (inset < 0) {
            return;
        }
        navSeen = inset;
        if (sheetView != null) {
            // the strip deliberately has no nav band inside it; writing it back here put a
            // nav-height stripe of empty paper inside the 72dp strip on the next inset dispatch
            sheetView.setPadding(0, 0, 0, peekShowing ? 0 : inset);
        }
    }

    /**
     * Decide whether the edge-to-edge window actually worked, and put it back if it did not.
     *
     * <p>Asking the window to draw under the bars is a request, and on a device where the answer
     * is different the panel can end up under the strip: a bar with the clock printed on it. The
     * request cannot be checked before the fact, so it is checked after the first real layout, on
     * evidence - the panel's own measured position - and reverted rather than argued with.</p>
     */
    static boolean needsEdgeFallback(int statusInsetPx, int surfaceTopPx, int surfacePadTopPx) {
        if (statusInsetPx <= 0) {
            // a device with no status bar cannot fail this way, and knows nothing yet
            return false;
        }
        // Two ways this can be wrong, and both are visible from the panel's screen position:
        //  - its content starts under the strip (the clock printed on our bar);
        //  - it has been pushed down, which is what a window still fitting its content does to a
        //    Gravity.TOP child.
        // The rim alone is not evidence: in the correct case it sits at 0 by design.
        return surfaceTopPx > 0 || surfaceTopPx + surfacePadTopPx < statusInsetPx;
    }

    static void checkEdgeWindow(Activity activity) {
        if (!edgeToEdge || edgeChecked || surfaceView == null || activity == null) {
            return;
        }
        if (surfaceView.getHeight() <= 0) {
            // not laid out yet: the next tick will have a real position to judge
            return;
        }
        edgeChecked = true;
        int[] at = new int[2];
        surfaceView.getLocationOnScreen(at);
        int top = at[1];
        int padTop = surfaceView.getPaddingTop();
        if (!needsEdgeFallback(statusInset, top, padTop)) {
            return;
        }
        edgeFellBack = true;
        edgeToEdge = false;
        SlothLog.error("the panel sat under the status strip, so the window stopped drawing under "
                + "the bars: content starts " + (top + padTop) + "px, strip " + statusInset + "px");
        try {
            Window window = activity.getWindow();
            if (window != null) {
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    window.setDecorFitsSystemWindows(true);
                } else {
                    View decor = window.getDecorView();
                    decor.setSystemUiVisibility(decor.getSystemUiVisibility()
                            & ~(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION));
                }
            }
        } catch (RuntimeException e) {
            SlothLog.error("could not restore the fitted window");
        }
        rebuild(activity);
    }

    /** Paint the status bar strip in the panel's colour and point the glyphs the right way. */
    static void paintStatusBar(Activity activity) {
        int colour = SlothTheme.surface();
        if (statusScrim != null) {
            statusScrim.setBackgroundColor(colour);
            ViewGroup.LayoutParams lp = statusScrim.getLayoutParams();
            if (lp != null && lp.height != statusInset) {
                lp.height = Math.max(0, statusInset);
                statusScrim.setLayoutParams(lp);
            }
        }
        // a light panel needs dark glyphs, and the other way round
        double l = 0.2126 * ((colour >> 16) & 0xFF) / 255.0
                + 0.7152 * ((colour >> 8) & 0xFF) / 255.0
                + 0.0722 * (colour & 0xFF) / 255.0;
        boolean darkGlyphs = l > 0.55;
        statusIconsLight = darkGlyphs;
        Window window = activity.getWindow();
        if (window == null) {
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
                controller.setSystemBarsAppearance(darkGlyphs ? mask : 0, mask);
            }
        } else {
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (darkGlyphs) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }
    }

    /** How much of the screen the system navigation bar owns. */
    static int navInset(Activity activity) {
        if (testNavInset >= 0) {
            return testNavInset;
        }
        if (navSeen >= 0) {
            return navSeen;
        }
        try {
            android.view.WindowInsets insets = activity.getWindow().getDecorView().getRootWindowInsets();
            if (insets != null) {
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    return insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom;
                }
                return insets.getSystemWindowInsetBottom();
            }
        } catch (RuntimeException ignored) {
        }
        return 0;
    }

    /** Tests set this instead of inventing a window; -1 means read the real window. */
    static int testNavInset = -1;
    private static int navSeen = -1;
    private static View sheetView;
    private static View surfaceView;

    /**
     * Height of the bar inside the surface, in pixels, from the last build.
     *
     * <p>The surface is sized from this and the drawer's height rather than left to wrap.
     * A FrameLayout that wraps its content measures a MATCH_PARENT child against the whole
     * window, and the painted backdrop is exactly that child - so a wrapping surface came
     * out full-screen and the scene sat over the page. It showed only until the drawer had
     * been given an explicit height once, which is why it looked intermittent.</p>
     */
    private static int surfaceBarPx;

    /** Whether the surface has been given a real height yet. */
    private static boolean surfaceSized;

    /** The surface is the bar plus however much drawer is showing, and nothing else. */
    static void applySurfaceHeight(Activity activity, int drawerHeight) {
        if (surfaceView == null || surfaceBarPx <= 0) {
            return;
        }
        ViewGroup.LayoutParams lp = surfaceView.getLayoutParams();
        if (lp == null) {
            return;
        }
        int height = surfaceBarPx + Math.max(0, drawerHeight);
        if (lp.height != height) {
            lp.height = height;
            surfaceView.setLayoutParams(lp);
        }
        surfaceSized = true;
    }

    /**
     * The status bar sits on the same colour as the drawer, so the surface reads as one
     * piece up to the top of the screen, and its icons flip to dark on the light themes.
     */
    static void tintStatusBar(Activity activity) {
        // the strip is a view of ours now. Setting a window colour as well would be a second
        // writer for the same band, and on Android 15 it is a writer the platform ignores.
        paintStatusBar(activity);
    }

    private static FrameLayout.LayoutParams fadeLp(Activity activity) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 26));
        lp.gravity = Gravity.BOTTOM;
        lp.bottomMargin = navInset(activity);
        return lp;
    }

    private static int screenWidth(Activity activity) {
        try {
            return activity.getResources().getDisplayMetrics().widthPixels;
        } catch (RuntimeException ignored) {
            return 1080;
        }
    }

    private static int statusBarHeight(Activity activity) {
        try {
            android.view.WindowInsets insets = activity.getWindow().getDecorView().getRootWindowInsets();
            if (insets != null) {
                // the typed inset is the one that stays correct under Android 15's
                // edge-to-edge window; the legacy getter is kept for the older API only
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    int top = insets.getInsets(android.view.WindowInsets.Type.statusBars()).top;
                    if (top > 0) {
                        return top;
                    }
                } else if (insets.getSystemWindowInsetTop() > 0) {
                    return insets.getSystemWindowInsetTop();
                }
            }
        } catch (Throwable ignored) {
        }
        int id = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? activity.getResources().getDimensionPixelSize(id) : dp(activity, 24);
    }

    /** What the address bar is showing, for the drawer's head line. */
    private static String thisSite(Activity activity) {
        try {
            View bar = resolve(activity, "urlBar");
            if (bar instanceof TextView) {
                CharSequence text = ((TextView) bar).getText();
                if (text != null && text.length() > 0) {
                    String value = text.toString().trim();
                    return value.length() > 48 ? value.substring(0, 47) + "\u2026" : value;
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /** Swiping the address moves along the tab strip, the way every mobile browser does. */
    private static void switchTab(Activity activity, int direction) {
        View strip = resolveStrip(activity);
        if (!(strip instanceof ViewGroup)) {
            return;
        }
        ViewGroup tabs = (ViewGroup) strip;
        int count = tabs.getChildCount();
        if (count < 2) {
            return;
        }
        int active = 0;
        for (int i = 0; i < count; i++) {
            View chip = tabs.getChildAt(i);
            if (chip.isSelected() || chip.isActivated()) {
                active = i;
                break;
            }
        }
        int next = Math.max(0, Math.min(count - 1, active + direction));
        if (next != active) {
            tabs.getChildAt(next).performClick();
        }
    }

    /** The browser's own counter if it has one, otherwise the strip itself. */
    private static int tabCount(Activity activity) {
        View counter = resolve(activity, "tabCount");
        if (counter instanceof TextView) {
            try {
                return Integer.parseInt(((TextView) counter).getText().toString().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        View strip = resolveStrip(activity);
        if (strip instanceof ViewGroup) {
            return Math.max(1, ((ViewGroup) strip).getChildCount());
        }
        return 1;
    }

    /** History buttons fade when the browser says there is nowhere to go. */
    /** Each control's own alpha (disabled = 0.42), multiplied by the current fade. */
    private static final java.util.WeakHashMap<View, Float> chromeBase = new java.util.WeakHashMap<>();

    static void applyChromeFade() {
        if (addressPillView != null) {
            addressPillView.setAlpha(Math.max(0.30f, chromeFade));
        }
        for (View view : chromeViews) {
            if (view == null) {
                continue;
            }
            Float base = chromeBase.get(view);
            float wanted = base == null ? 1f : base;
            view.setAlpha(Math.max(0.06f, wanted * chromeFade));
        }
    }

    private static void dimWhenUnusable(Activity activity, String bindingName, View glyph) {
        if (glyph == null) {
            return;
        }
        View real = resolve(activity, bindingName);
        boolean usable = real == null || real.isEnabled();
        float want = usable ? 1f : 0.42f;
        chromeBase.put(glyph, want);
        glyph.setAlpha(Math.max(0.06f, want * chromeFade));
    }

    /* ---------------------------------------------------------------- refresh */
    /**
     * A light, steady tick.
     *
     * <p>refresh() is a full reconciliation and too heavy to run on a timer. But the page
     * changes without us - a tap on a link, a tab closed from the browser's own switcher -
     * and without a tick the address field and the tab count only caught up when the owner
     * touched something of ours. The bar has to follow the page on its own.</p>
     */
    static void tick() {
        // read the owner at tick time: a rotation replaces it, and a loop that captured the
        // old activity would go quiet for the rest of the session
        final Activity activity = owner;
        if (activity == null || activity.isFinishing()) {
            return;
        }
        try {
            View real = resolve(activity, "urlBar");
            if (real instanceof EditText && address != null && !address.hasFocus()) {
                CharSequence text = ((EditText) real).getText();
                if (text != null && !text.toString().contentEquals(address.getText())) {
                    address.setText(text.toString());
                    address.setSelection(address.getText().length());
                }
            }
            // the app can leave the page at zero height on its own - a restart, a cleared
            // engine - and a black window with an address in it is the worst thing to hand
            // someone silently, so the tick checks rather than waits for a report
            ensurePageVisible(activity);
            // asked once the window is laid out: this is the only evidence the request worked
            checkEdgeWindow(activity);
            syncHistory(activity);
            if (board != null && address != null) {
                // the drawer's address line is the same fact as the bar's, so it is kept
                // the same way: on the timer, not only when the drawer is rebuilt
                board.onSite(String.valueOf(address.getText()));
            }
            int count = tabCount(activity);
            if (TabFace != null && !String.valueOf(count).contentEquals(TabFace.getText())) {
                TabFace.setText(String.valueOf(count));
            }
            if (board != null) {
                board.onTabCount(count);
                if (peekShowing) {
                    // the strip is the tabs: it cannot be a frame behind them
                    fillPeek(activity);
                }
            }
            sweepTicks++;
            if (sweepTicks % 4 == 0) {
                // every few seconds, not every tick: the app can open its own panel or
                // bring its old chrome back after we last collapsed it, and a box left
                // over the page is the thing the owner keeps photographing
                sweepAppLeftovers(activity);
            }
        } catch (Throwable t) {
            SlothLog.warn("tick failed: " + t.getClass().getSimpleName());
        }
    }

    private static void refresh(Activity activity) {
        try {
            ensurePageVisible(activity);
            View real = resolve(activity, "urlBar");
            if (real instanceof EditText && address != null && !address.hasFocus()) {
                CharSequence text = ((EditText) real).getText();
                if (text != null && !text.toString().contentEquals(address.getText())) {
                    address.setText(text.toString());
                    address.setSelection(address.getText().length());
                }
            }

            if (board != null) {
                board.mirror(activity);
            }
            if (TabFace != null) {
                TabFace.setText(String.valueOf(tabCount(activity)));
            }
            if (board != null) {
                board.onSite(thisSite(activity));
            board.onTabCount(tabCount(activity));
            tintStatusBar(activity);
            refinePeek(activity);
            }
            if (settingsOpen) {
                // the app closes its own panel on back; if it did, stop lending it
                View panelNow = resolve(activity, "panel");
                if (panelNow != null && panelNow.getVisibility() != View.VISIBLE) {
                    leaveSettings(activity);
                }
            }
            // the app's own bar is never allowed back, loan or no loan
            for (String name : CHROME) {
                View chromeNow = resolve(activity, name);
                if (chromeNow != null && chromeNow.getVisibility() != View.GONE) {
                    collapseView(chromeNow);
                }
            }
            if (!settingsOpen) {
                freeLegacyPull(activity);
                if (!dragging && !openIntent && drawer != null
                        && drawer.getVisibility() == View.VISIBLE
                        && drawer.getLayoutParams().height > 0) {
                    // nothing asked for the drawer to be open: a leftover height is debris
                    setDrawerHeight(activity, 0);
                }
                for (String name : CHROME) {
                    View legacy = resolve(activity, name);
                    if (legacy != null && legacy.getVisibility() != View.GONE) {
                        collapseView(legacy);
                    }
                }
                for (String name : PANEL) {
                    View legacy = resolve(activity, name);
                    if (legacy != null && legacy.getVisibility() != View.GONE) {
                        collapseView(legacy);
                    }
                }
            }
            dimWhenUnusable(activity, "btnBack", backGlyph);
            dimWhenUnusable(activity, "btnFwd", fwdGlyph);
        } catch (Throwable t) {
            // this method touches the app's own view tree, which changes under us. Swallowed
            // silently it hid every failure it ever had; a line in the log costs nothing
            SlothLog.warn("refresh failed: " + t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : " " + t.getMessage()));
        }
    }

    /** A tab chip carries its title in a TextView somewhere inside it. */
    static CharSequence label(View v) {
        if (v instanceof TextView) {
            CharSequence t = ((TextView) v).getText();
            if (t != null && t.length() > 0) {
                return t;
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                CharSequence found = label(g.getChildAt(i));
                if (found != null && found.length() > 0) {
                    return found;
                }
            }
        }
        return v.getContentDescription();
    }

    /* ----------------------------------------------------------------- widgets */
    private static TextView label(Activity activity, String text) {
        TextView t = new TextView(activity);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        t.setLetterSpacing(0.1f);
        t.setTextColor(DIM);
        t.setPadding(0, dp(activity, 10), 0, dp(activity, 6));
        return t;
    }

    private static TextView chip(Activity activity, String text, View.OnClickListener onClick) {
        TextView t = new TextView(activity);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        t.setTextColor(INK);
        t.setGravity(Gravity.CENTER);
        t.setContentDescription(text);
        t.setFocusable(true);
        t.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
        // the footer pills read as disabled: a border a shade off the panel is not an edge
        t.setBackground(round(activity, 99, CARD, SlothTheme.hairline(0x40)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 34));
        lp.setMargins(0, 0, dp(activity, 6), 0);
        t.setLayoutParams(lp);
        t.setOnClickListener(onClick);
        return t;
    }

}
