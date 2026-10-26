package com.sloth.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Layout proof for the shell, on the JVM. Robolectric lays out real Android views,
 * so the capsule bar, the drawer's three states and the address mirror can be
 * checked without a phone.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class SlothShellTest {

    /** The fields the shell reaches for through the app's binding object. */
    public static class FakeBinding {
        public View btnBack, btnFwd, btnReload, btnPlus, btnMenu, btnExt, btnHome, btnPanic;
        public EditText urlBar;
        public View tabCount, pageInfo;
        public View panel, panelBody, chromeGrip, chromeToolRow, chromeFavRow, chromeMore, chromeWidgetGrid;
        public LinearLayout slotTabs;
        public FrameLayout chromeDock, chromeMain;
    }

    public static class TestActivity extends Activity {

        /** A page view, so the cover probe has one to reason about. */
        @SuppressWarnings("unused")
        private org.mozilla.geckoview.GeckoView geckoView;

        /** The app's own panel screen: it accepts the request and draws nothing. */
        public String lastPanel;

        public void openPanel(String kind) {
            lastPanel = kind;
        }

        /** Attach a page view the way the app holds one: a field on the activity. */
        void attachPage(org.mozilla.geckoview.GeckoView view) {
            this.geckoView = view;
        }

        public final FakeBinding binding = new FakeBinding();
        public int backClicks, plusClicks, tabClicks, switcherCalls;
        public String settingsSection;
        public String lastGo;

        // private on purpose: this is how MainActivity declares it, and a public-only
        // lookup found nothing, which is why Settings did nothing at all
        private void showSettings(String section) {
            settingsSection = section;
        }

        public final com.sloth.browser.engine.SlothSettings settings =
                new com.sloth.browser.engine.SlothSettings();
        public int settingsSaved;

        private void saveSettings() {
            settingsSaved++;
        }

        private int cleared, exported, vaults, restarts;

        private void clearData() {
            cleared++;
        }

        private void exportSettings() {
            exported++;
        }

        private void exportVault() {
            vaults++;
        }

        private void startEngine() {
            restarts++;
        }

        private String searchName() {
            return "DuckDuckGo";
        }

        private void showSwitcher() {
            switcherCalls++;
        }

        public void go(String uri) {
            lastGo = uri;
        }
    }

    private static final class Counter implements View.OnClickListener {
        int clicks;

        @Override
        public void onClick(View v) {
            clicks++;
        }
    }

    /**
     * Robolectric keeps statics alive between tests, and the shell is written for a
     * single activity, so each test starts from a clean slate.
     */
    @org.junit.Before
    public void resetShellStatics() {
        for (java.lang.reflect.Field f : SlothShell.class.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            f.setAccessible(true);
            try {
                Class<?> type = f.getType();
                if (type == int.class) {
                    f.setInt(null, "dockH".equals(f.getName()) ? -1 : 0);
                } else if (type == boolean.class) {
                    f.setBoolean(null, false);
                } else if (!type.isPrimitive()) {
                    f.set(null, null);
                }
            } catch (IllegalAccessException ignored) {
            }
        }
    }

    private TestActivity build() {
        TestActivity activity = Robolectric.buildActivity(TestActivity.class).create().get();
        FakeBinding b = activity.binding;

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        b.chromeDock = new FrameLayout(activity);
        b.chromeDock.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 52));
        ViewGroup.MarginLayoutParams dockLp =
                new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52);
        dockLp.topMargin = 6;
        dockLp.bottomMargin = 6;
        b.chromeDock.setLayoutParams(dockLp);
        b.chromeDock.setPadding(4, 0, 4, 0);

        b.chromeMain = new FrameLayout(activity);
        b.chromeMain.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 44));
        b.chromeDock.addView(b.chromeMain);
        root.addView(b.chromeDock);

        b.urlBar = new EditText(activity);
        // the browser's own tab strip: tests fill it, and the drawer mirrors it
        b.slotTabs = new LinearLayout(activity);
        b.slotTabs.setTag("tab-squares");
        root.addView(b.slotTabs);
        b.btnBack = tagged(activity, "btnBack");
        b.btnFwd = tagged(activity, "btnFwd");
        b.btnReload = tagged(activity, "btnReload");
        b.btnPlus = tagged(activity, "btnPlus");
        b.btnMenu = tagged(activity, "btnMenu");
        b.btnExt = tagged(activity, "btnExt");
        android.widget.TextView counter = new android.widget.TextView(activity);
        counter.setText("3");
        b.tabCount = counter;
        b.pageInfo = tagged(activity, "pageInfo");
        b.chromeGrip = new View(activity);
        b.chromeToolRow = new View(activity);
        b.chromeFavRow = new View(activity);
        b.chromeMore = new View(activity);
        b.chromeWidgetGrid = new View(activity);
        FrameLayout panel = new FrameLayout(activity);
        FrameLayout panelBody = new FrameLayout(activity);
        panel.addView(panelBody);
        b.panel = panel;
        b.panelBody = panelBody;
        root.addView(b.panel);
        b.btnHome = new View(activity);
        b.btnPanic = new View(activity);

        activity.setContentView(root);
        return activity;
    }

    /** The drawer sits under the scrim that closes it. */
    private int countByTag(View v, String tag) {
        int count = tag.equals(v.getTag()) ? 1 : 0;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                count += countByTag(g.getChildAt(i), tag);
            }
        }
        return count;
    }

    private boolean isShown(View v) {
        View current = v;
        while (current != null) {
            if (current.getVisibility() != View.VISIBLE) {
                return false;
            }
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return true;
    }

    /** The first ScrollView under this root. */
    private View scrollerIn(View v) {
        if (v instanceof android.widget.ScrollView) {
            return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View found = scrollerIn(g.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** A scroll of the list is not a flick of the sheet. */
    @Test
    public void scrollingTheListDoesNotReleaseTheSheet() {
        TestActivity activity = build();
        final int[] releases = {0};
        final int[] claimed = {0};
        SlothSheetScroll list = new SlothSheetScroll(activity);
        list.setListener(new SlothBar.Listener() {
            @Override
            public void onDragStart(float y, float carry) {
                claimed[0]++;
            }

            @Override
            public void onDrag(float dy) {
            }

            @Override
            public void onRelease(float velocity) {
                releases[0]++;
            }

            @Override
            public void onTap() {
            }

            @Override
            public boolean onSwipe(float dx) {
                return false;
            }
        });

        // a scroll that never goes near the top: down, then a fast finger lift
        long t = SystemClock.uptimeMillis();
        list.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 400f, 0));
        list.onTouchEvent(MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_MOVE, 200f, 430f, 0));
        list.onTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_MOVE, 200f, 470f, 0));
        list.onTouchEvent(MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, 200f, 600f, 0));

        assertEquals("a scroll is not a drag of the drawer", 0, claimed[0]);
        assertEquals("and it must not report a release either: that settled the sheet "
                + "from a finger that never touched it", 0, releases[0]);
    }

    /** The strip's drag has to work from a tab square, not only from the gaps. */
    @Test
    public void theStripDragCoversEverySquare() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        addTabs(activity, "one", "two");
        // open it to the strip
        driveHeight(activity, peekHeight(activity));
        settle();

        View strip = findByViewTag(overlayOf(activity), "peek-strip");
        assertNotNull("the peek strip", strip);
        ViewGroup row = (ViewGroup) ((ViewGroup) strip).getChildAt(0);
        assertTrue("the strip has squares in it", row.getChildCount() >= 2);

        int before = drawerOf(activity).getLayoutParams().height;
        // a finger that lands on the first square and pulls up: the sheet must follow.
        // This is the case that used to do nothing at all, because the touch was delivered
        // to the square and the scroll container's listener never saw it
        View square = row.getChildAt(0);
        long t = SystemClock.uptimeMillis();
        square.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 30f, 30f, 0));
        square.dispatchTouchEvent(MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_MOVE, 30f, 10f, 0));
        square.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_MOVE, 30f, -10f, 0));
        settle();
        int after = drawerOf(activity).getLayoutParams().height;
        assertTrue("a drag that starts on a tab square must move the sheet, not ignore the "
                + "finger: " + before + " -> " + after, after != before);
    }

    /** A drag must move the page with the compositor, not re-lay-it-out. */
    @Test
    public void thePageFollowsByTranslationWhileDragging() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        driveHeight(activity, peekHeight(activity) * 4);
        settle();

        View page = pageRoot(activity);
        int committed = page.getPaddingTop();

        View bar = findByViewTag(overlayOf(activity), "drag");
        assertNotNull("the bar is the handle", bar);
        long t = SystemClock.uptimeMillis();
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 300f, 0));
        for (int i = 1; i <= 12; i++) {
            bar.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 16L,
                    MotionEvent.ACTION_MOVE, 200f, 300f - i * 12f, 0));
        }
        settle();

        assertEquals("while dragging, the page root's padding must not be rewritten: a "
                        + "padding write is a layout pass that resizes the engine surface",
                committed, page.getPaddingTop());
        assertNotEquals("the page follows the sheet by translation instead", 0f,
                page.getTranslationY(), 0.5);

        // on release the real offset is committed once and the translation cleared
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t + 13 * 16L,
                MotionEvent.ACTION_UP, 200f, 300f - 12 * 12f, 0));
        settle();
        assertEquals("the translation is cleared at rest", 0f, page.getTranslationY(), 0.5);
    }

    /** The app's own content view, which the shell offsets. */
    private View pageRoot(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        return content.getChildAt(0);
    }

    /** Coalescing must not put the sheet behind the finger. */
    @Test
    public void theSheetStillFollowsTheFingerExactly() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        // open it tall enough that a drag up is a real drag, not a sheet already at zero
        driveHeight(activity, peekHeight(activity) * 4);
        settle();
        int start = drawerOf(activity).getLayoutParams().height;
        assertTrue("the sheet is open: " + start, start > 200);

        View bar = findByViewTag(overlayOf(activity), "drag");
        long t = SystemClock.uptimeMillis();
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 300f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t + 16, MotionEvent.ACTION_MOVE, 200f, 290f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t + 32, MotionEvent.ACTION_MOVE, 200f, 240f, 0));
        settle();

        int now = drawerOf(activity).getLayoutParams().height;
        // the touch coordinates are pixels and so is the drawer height: 60px of finger
        // must be 60px of sheet, not 60px minus a frame or scaled by density
        assertEquals("after 60px up the sheet sits 60px shorter: " + start + " -> " + now,
                60, start - now, 4.0);
    }

    /** The app's panel, left visible with nothing in it, must be put away on a timer. */
    @Test
    public void aLeftoverPanelIsSweptAway() {
        TestActivity activity = build();
        SlothShell.own(activity);
        activity.binding.panel.setVisibility(View.VISIBLE);
        activity.binding.panelBody.setVisibility(View.VISIBLE);

        SlothShell.sweepAppLeftovers(activity);

        assertEquals("the app's panel is not this shell's to leave open",
                View.GONE, activity.binding.panel.getVisibility());
        assertEquals(View.GONE, activity.binding.panelBody.getVisibility());
    }

    /** Collapsing the panel alone leaves the card that held it: an empty bordered box. */
    @Test
    public void theBoxAroundACollapsedPanelGoesWithIt() {
        TestActivity activity = build();
        SlothShell.own(activity);
        ViewGroup content = activity.findViewById(android.R.id.content);
        ViewGroup appRoot = (ViewGroup) content.getChildAt(0);
        FrameLayout card = new FrameLayout(activity);
        FrameLayout inner = new FrameLayout(activity);
        card.addView(inner);
        appRoot.addView(card);

        // the order the shell uses: collapse the panel, then the box that held it
        inner.setVisibility(View.GONE);
        SlothShell.collapseLonelyAncestors(inner);

        assertEquals("and the box that existed only to hold it", View.GONE, card.getVisibility());
    }

    /** The next "it still feels wrong" should arrive with numbers, not adjectives. */
    @Test
    public void aDragLeavesATraceInDiagnostics() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        driveHeight(activity, peekHeight(activity) * 4);
        settle();

        View bar = findByViewTag(overlayOf(activity), "drag");
        long t = SystemClock.uptimeMillis();
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 300f, 0));
        for (int i = 1; i <= 6; i++) {
            bar.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 16L,
                    MotionEvent.ACTION_MOVE, 200f, 300f - i * 15f, 0));
        }
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t + 7 * 16L,
                MotionEvent.ACTION_UP, 200f, 300f - 90f, 0));
        settle();

        String trace = SlothShell.dragTraceForTest();
        assertFalse("a drag was not recorded: " + trace, trace.startsWith("-1"));
        assertTrue("the trace names the move count: " + trace, trace.contains("moves="));
        assertTrue("and the seat it settled on: " + trace, trace.contains("seat="));
        String report = SlothShell.diagLines(activity);
        assertTrue("diagnostics prints it: " + report, report.contains("last drag:"));
    }

    /** The strip the status icons sit on is ours, in the panel's colour, with matching glyphs. */
    @Test
    public void theStatusStripIsPaintedInThePanelsOwnColour() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();

        ViewGroup root = overlayOf(activity);
        View scrim = findByViewTag(root, "status-scrim");
        assertNotNull("no status strip was painted", scrim);
        assertEquals("the strip is the status bar's height",
                SlothShell.statusForTest(), scrim.getLayoutParams().height);
        android.graphics.drawable.ColorDrawable bg =
                (android.graphics.drawable.ColorDrawable) scrim.getBackground();
        assertEquals("in the panel's colour, not the system's", SlothTheme.surface(), bg.getColor());
        boolean light = SlothShell.statusIconsLight;
        assertTrue("glyphs are pointed the right way for that colour",
                light == (SlothTheme.surface() > 0xFF808080));
    }

    /** One fill, on one surface: the bar must not paint a second rounded rect inside it. */
    @Test
    public void theBarDoesNotPaintASecondFill() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        View bar = findByViewTag(overlayOf(activity), "drag");
        assertNull("the bar carries its own background again", bar.getBackground());
        View surface = findByViewTag(overlayOf(activity), "surface");
        assertNotNull("and the surface carries the fill", surface.getBackground());
    }

    /** The window draws under the system bars, and nothing is counted twice for it. */
    @Test
    public void thePageReachesTheTopOfTheScreenWithoutDoubleCountingTheStatusBar() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();

        assertTrue("the window was asked to draw under the system bars", SlothShell.edgeToEdge);
        View surface = findByViewTag(overlayOf(activity), "surface");
        float density = activity.getResources().getDisplayMetrics().density;
        assertEquals("the panel keeps its gap below the strip, once",
                SlothShell.statusForTest() + Math.round(8 * density), surface.getPaddingTop());
    }

    /** The page clears the nav bar, since the window now runs beneath it. */
    @Test
    public void thePageKeepsItsDistanceFromTheNavBar() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        assertTrue("the page pads its bottom by at least the nav bar",
                SlothShell.pagePadBottomForTest() >= SlothShell.navInset(activity));
    }

    /** The edge-to-edge request is judged on evidence, and reverted rather than argued with. */
    @Test
    public void aPanelUnderTheStatusStripRevertsTheWindow() {
        // the rim at 0 with content cleared is the correct case, and the common one
        assertFalse("a rim at the top with content cleared is correct",
                SlothShell.needsEdgeFallback(96, 0, 104));
        assertTrue("content printed under the clock means the request failed",
                SlothShell.needsEdgeFallback(96, 0, 40));
        assertTrue("a panel pushed down the screen means the window still fits its content",
                SlothShell.needsEdgeFallback(96, 96, 104));
        assertFalse("a device without a strip cannot fail this way",
                SlothShell.needsEdgeFallback(0, 0, 0));
    }

    /** Falling back is a one-way move for that window: it must not flip back on the next tick. */
    @Test
    public void theFallbackIsCheckedOnce() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        SlothShell.checkEdgeWindow(activity);
        boolean first = SlothShell.edgeToEdge;
        SlothShell.checkEdgeWindow(activity);
        assertEquals("the decision does not change on a later tick", first, SlothShell.edgeToEdge);
    }


    private View findByDescription(View root, String description) {
        final View[] found = new View[1];
        walk(root, new ViewVisitor() {
            @Override
            public void visit(View v) {
                if (found[0] == null && description.equals(String.valueOf(v.getContentDescription()))) {
                    found[0] = v;
                }
            }
        });
        return found[0];
    }

    private SlothIcons firstIcon(View root) {
        final SlothIcons[] found = new SlothIcons[1];
        walk(root, new ViewVisitor() {
            @Override
            public void visit(View v) {
                if (found[0] == null && v instanceof SlothIcons) {
                    found[0] = (SlothIcons) v;
                }
            }
        });
        return found[0];
    }

    /** The four quick actions are drawn shapes, not another font's idea of them. */
    @Test
    public void theQuickActionsAreDrawnNotTyped() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();

        java.util.Map<String, SlothIcons.Kind> wanted = new java.util.LinkedHashMap<>();
        wanted.put("Home", SlothIcons.Kind.HOME);
        wanted.put("Bookmarks", SlothIcons.Kind.BOOKMARK);
        wanted.put("History", SlothIcons.Kind.CLOCK);
        wanted.put("Downloads", SlothIcons.Kind.DOWNLOAD);

        for (java.util.Map.Entry<String, SlothIcons.Kind> e : wanted.entrySet()) {
            View cell = findByDescription(overlayOf(activity), e.getKey());
            assertNotNull(e.getKey() + " has a cell", cell);
            SlothIcons icon = firstIcon(cell);
            assertNotNull(e.getKey() + " is drawn, not typed", icon);
            assertEquals(e.getKey() + " draws the right shape", e.getValue(), icon.kind());
        }
        // and the characters that used to carry them are gone from the tree
        String tree = dumpText(overlayOf(activity));
        assertFalse("no home glyph left: " + tree, tree.contains("\u2302"));
        assertFalse("no flag glyph left: " + tree, tree.contains("\u2691"));
        assertFalse("no arrow glyph left: " + tree, tree.contains("\u2193"));
    }

    /** A window that was reverted is not asked again by the rebuild it caused. */
    @Test
    public void aRevertedWindowStaysReverted() throws Exception {
        TestActivity activity = build();
        java.lang.reflect.Field flag = SlothShell.class.getDeclaredField("edgeFellBack");
        flag.setAccessible(true);
        flag.set(null, true);
        try {
            SlothShell.own(activity);
            settle();
            assertFalse("the rebuild must not re-ask for edge to edge", SlothShell.edgeToEdge);
        } finally {
            flag.set(null, false);
        }
    }

    /** The strip keeps no nav band inside it, however often the window reports its insets. */
    @Test
    public void thePeekStripDoesNotGrowANavBarBand() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        driveHeightSafe2(activity, peekHeight(activity) + 4);
        settle();
        SlothShell.applyBottomInset(activity, 48);
        settle();
        assertEquals("a strip is 72dp of tabs, not tabs plus a nav band",
                0, drawerOf(activity).getPaddingBottom());
    }

    /** Detaching the shell releases the page it was holding, and the window facts with it. */
    @Test
    public void releasingTheShellLetsGoOfThePage() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        SlothShell.releaseShell();
        assertEquals("the page's padding is forgotten with the window",
                -1, SlothShell.pagePadBottomForTest());
    }

    /** Every drawn bar control stays in the accessibility tree, because each one is labelled. */
    @Test
    public void drawnBarControlsStayInTheAccessibilityTree() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        View reload = findByDescription(overlayOf(activity), "Reload");
        assertNotNull("the reload control", reload);
        assertTrue("a labelled control must not be marked decorative",
                reload.getImportantForAccessibility() != View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** A refresh that changes nothing must not rebuild the tab row. */
    @Test
    public void anUnchangedRefreshKeepsTheSameCards() throws Exception {
        TestActivity activity = build();
        addTabs(activity, "Docs", "Mail");
        SlothShell.own(activity);
        settle();
        ViewGroup row = (ViewGroup) findByViewTag(drawerOf(activity), "tab-row");
        assertNotNull("a tab row", row);
        Object before = row.getChildAt(0);
        SlothShell.refreshForTest(activity);
        settle();
        assertTrue("the row was torn down and rebuilt for no change",
                before == row.getChildAt(0));
    }

    /** The settings page does not eat BACK: the shell's own order has to run. */
    @Test
    public void theSettingsPageLeavesBackToTheShell() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        SlothShell.openSettingsPage(activity);
        settle();
        View page = findByViewTag(overlayOf(activity), "settings-page");
        assertNotNull("a settings page", page);
        boolean consumed = page.dispatchKeyEvent(new android.view.KeyEvent(
                android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK));
        assertFalse("the page answered BACK itself, skipping the shell's order", consumed);
    }

    /** A failed read of a setting shows the browser's default, not the opposite of it. */
    @Test
    public void settingsFallBackToTheBrowsersOwnDefaults() {
        assertTrue("javascript ships on", SlothSettings.defaultFor("javascript"));
        assertTrue("cookies ship on", SlothSettings.defaultFor("acceptCookies"));
        assertTrue("autoplay blocking ships on", SlothSettings.defaultFor("blockAutoplay"));
        assertTrue("tracker blocking ships on", SlothSettings.defaultFor("blockTrackers"));
        assertTrue("screenshot blocking ships on", SlothSettings.defaultFor("screenGuard"));
        assertFalse("and a setting that ships off stays off", SlothSettings.defaultFor("popups"));
        assertEquals("text zoom ships at 100%", 100, SlothSettings.defaultNumberFor("textZoom"));
        assertEquals("auto-lock ships at five minutes", 5,
                SlothSettings.defaultNumberFor("autoLockMin"));
    }

    /** Nothing in the widget grid may measure itself zero tall. */
    @Test
    public void theWidgetRowsHaveAHeightOfTheirOwn() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        View grid = findByViewTag(drawerOf(activity), "widget-grid");
        assertNotNull("a widget grid", grid);
        ViewGroup rows = (ViewGroup) grid;
        int checked = 0;
        for (int i = 0; i < rows.getChildCount(); i++) {
            View child = rows.getChildAt(i);
            android.view.ViewGroup.LayoutParams lp = child.getLayoutParams();
            if (lp == null) {
                continue;
            }
            if (lp instanceof LinearLayout.LayoutParams && ((LinearLayout.LayoutParams) lp).weight > 0) {
                org.junit.Assert.fail("a row weighted inside a wrap-content column: child " + i);
            }
            assertFalse("child " + i + " asks for height 0", lp.height == 0);
            if (child instanceof ViewGroup) {
                ViewGroup inner = (ViewGroup) child;
                for (int j = 0; j < inner.getChildCount(); j++) {
                    android.view.ViewGroup.LayoutParams ilp = inner.getChildAt(j).getLayoutParams();
                    if (ilp instanceof LinearLayout.LayoutParams
                            && ((LinearLayout.LayoutParams) ilp).weight > 0) {
                        assertEquals("a weighted tile must measure its own height inside a "
                                + "wrap-content row: child " + i + "/" + j,
                                ViewGroup.LayoutParams.WRAP_CONTENT, ilp.height);
                        checked++;
                    }
                }
            }
        }
        assertTrue("the grid has weighted tiles to check", checked > 0);
    }

    /** Switching the calculator off takes its keypad with it, and leaving it off leaves it off. */
    @Test
    public void hidingTheCalculatorHidesItsKeypad() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        View pad = findByViewTag(drawerOf(activity), "calc-pad");
        assertNotNull("a keypad", pad);
        SlothBoard board = SlothShell.boardForTest();
        assertNotNull("a board", board);
        board.toggleWidget("calc");
        settle();
        assertEquals("the keypad outlived its card", View.GONE, pad.getVisibility());
        board.toggleWidget("calc");
        settle();
        assertEquals("the keypad did not come back with its card", View.VISIBLE,
                pad.getVisibility());
    }

    /** Bookmarks opens a list of our own, not a loaned blank panel. */
    @Test
    public void bookmarksOpenTheLibraryPage() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        SlothShell.openLibrary(activity, "bookmarks");
        settle();
        View page = findByViewTag(overlayOf(activity), "library-page");
        assertNotNull("a library page", page);
        assertEquals("the library page is showing", View.VISIBLE, page.getVisibility());
        assertTrue("the page says what it found rather than staying blank",
                dumpText(page).trim().length() > "BookmarksBack".length());
    }

    /** BACK closes the library before it reaches the drawer. */
    @Test
    public void backClosesTheLibraryFirst() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        SlothShell.openLibrary(activity, "history");
        settle();
        View page = findByViewTag(overlayOf(activity), "library-page");
        assertEquals(View.VISIBLE, page.getVisibility());
        assertTrue("back closed a layer", SlothShell.backOneLayer(activity));
        assertEquals("the library page should be the layer back closed",
                View.GONE, page.getVisibility());
    }

    /** Relative times read like a person wrote them, and a missing stamp says nothing at all. */
    @Test
    public void relativeTimesAreWords() {
        long now = System.currentTimeMillis();
        assertEquals("just now", SlothLibrary.ago(now - 5_000L));
        assertEquals("1 minute ago", SlothLibrary.ago(now - 65_000L));
        assertEquals("2 hours ago", SlothLibrary.ago(now - 2L * 3600_000L));
        assertEquals("3 days ago", SlothLibrary.ago(now - 3L * 86_400_000L));
        assertEquals("2 months ago", SlothLibrary.ago(now - 65L * 86_400_000L));
        assertEquals("a stamp we do not have prints nothing", "", SlothLibrary.ago(0));
    }

    /** Saving with no page open says so instead of saving a blank bookmark. */
    @Test
    public void savingWithNoPageOpenSaysSo() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        SlothShell.openLibrary(activity, "bookmarks");
        settle();
        View page = findByViewTag(overlayOf(activity), "library-page");
        View save = findByViewTag(page, "library-save");
        assertNotNull("the bookmarks page offers to save the page you are on", save);
        assertEquals("the offer is on the bookmarks tab only", View.VISIBLE, save.getVisibility());
        save.performClick();
        settle();
        assertTrue("it says why rather than appearing to save",
                dumpText(page).toLowerCase().contains("nothing to save"));
    }

    /** Opening the library in a second window fills that window's page, not the first one's. */
    @Test
    public void theLibraryFollowsItsOwnWindow() throws Exception {
        TestActivity first = build();
        SlothShell.own(first);
        settle();
        SlothShell.openLibrary(first, "bookmarks");
        settle();

        TestActivity second = build();
        SlothShell.own(second);
        settle();
        SlothShell.openLibrary(second, "history");
        settle();

        View page = findByViewTag(overlayOf(second), "library-page");
        assertNotNull("the second window has a library page", page);
        assertEquals("it is showing", View.VISIBLE, page.getVisibility());
        String text = dumpText(page);
        assertTrue("the page says History, not the first window's Bookmarks: " + text,
                text.contains("History"));
        assertFalse("and it is not left holding a stale title: " + text, text.startsWith("Bookmarks"));
    }

    /** A fault is not evicted by a busy minute of notes. */
    @Test
    public void aFaultOutlivesTheNotesAroundIt() {
        SlothLog.clear();
        SlothLog.error("the cause of the black page");
        for (int i = 0; i < 300; i++) {
            SlothLog.note("probe line " + i);
        }
        String text = SlothLog.text();
        assertTrue("the fault survived 300 notes: " + text, text.contains("the cause of the black page"));
        assertTrue("and it is reported before the noise", text.indexOf("FAULTS") < text.indexOf("RECENT"));
        assertEquals("the fault is counted with the notes", 121, SlothLog.count());
        SlothLog.clear();
    }

    /** Notes alone still read as a plain list, without a FAULTS heading. */
    @Test
    public void notesWithoutFaultsHaveNoEmptySection() {
        SlothLog.clear();
        SlothLog.note("only a note");
        String text = SlothLog.text();
        assertFalse("no empty faults section: " + text, text.contains("FAULTS"));
        assertTrue(text.contains("only a note"));
        SlothLog.clear();
    }

    /** A history list from the vault renders rows with titles and relative times. */
    @Test
    @org.robolectric.annotation.Config(application = FakeApp.class)
    public void historyRowsComeFromTheVault() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        FakeApp app = (FakeApp) activity.getApplication();
        long now = System.currentTimeMillis();
        app.vault.history.add(new com.sloth.browser.vault.HistoryEntry(
                "https://example.org/one", "First page", now - 2L * 3600_000L));
        app.vault.history.add(new com.sloth.browser.vault.HistoryEntry(
                "https://example.net/two", "", now - 5L * 86_400_000L));

        SlothShell.openLibrary(activity, "history");
        settle();
        View page = findByViewTag(overlayOf(activity), "library-page");
        String text = dumpText(page);
        assertTrue("the first title is listed: " + text, text.contains("First page"));
        assertTrue("with its age: " + text, text.contains("2 hours ago"));
        assertTrue("a nameless entry falls back to its URL: " + text,
                text.contains("https://example.net/two"));
        assertTrue("and still has an age: " + text, text.contains("5 days ago"));
        assertTrue("the count says two", text.contains("2 pages"));
    }

    /** Downloads come from the vault too, named rather than dressed as history. */
    @Test
    @org.robolectric.annotation.Config(application = FakeApp.class)
    public void downloadRowsComeFromTheVault() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        FakeApp app = (FakeApp) activity.getApplication();
        app.vault.downloads.add(new com.sloth.browser.vault.DownloadEntry(
                "manual.pdf", "https://example.org/manual.pdf", System.currentTimeMillis() - 60_000L));

        SlothShell.openLibrary(activity, "downloads");
        settle();
        String text = dumpText(findByViewTag(overlayOf(activity), "library-page"));
        assertTrue("the title is Downloads: " + text, text.contains("Downloads"));
        assertTrue("the file is listed: " + text, text.contains("manual.pdf"));
        assertTrue("with its age: " + text, text.contains("1 minute ago"));
    }

    /** Saving the page on screen writes the app's own Bookmark into the live list. */
    @Test
    @org.robolectric.annotation.Config(application = FakeApp.class)
    public void savingAPageAddsABookmarkOnce() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        ((android.widget.EditText) activity.binding.urlBar).setText("https://example.org/leaf");
        FakeApp app = (FakeApp) activity.getApplication();

        SlothShell.openLibrary(activity, "bookmarks");
        settle();
        View page = findByViewTag(overlayOf(activity), "library-page");
        View save = findByViewTag(page, "library-save");
        save.performClick();
        settle();

        assertEquals("one bookmark was added", 1, app.vault.bookmarks.size());
        assertEquals("and the vault was asked to keep it", 1, app.vault.persisted);
        Object entry = app.vault.bookmarks.get(0);
        assertEquals("https://example.org/leaf",
                entry.getClass().getMethod("getUrl").invoke(entry));
        assertEquals("the title is the host, not the whole URL", "example.org",
                entry.getClass().getMethod("getTitle").invoke(entry));
        assertTrue("the list refreshes to show it: " + dumpText(page),
                dumpText(page).contains("example.org"));

        save.performClick();
        settle();
        assertEquals("saving twice does not duplicate", 1, app.vault.bookmarks.size());
        assertTrue("and says so: " + dumpText(page), dumpText(page).contains("Already saved"));
    }

    /** A flick always moves somewhere, even from a seat. */
    @Test
    public void aFlickAlwaysMoves() {
        int[] seats = {0, 72, 400, 800};
        assertEquals("downward from shut: the next seat up", 72,
                SlothSeats.choose(seats, 0, 1.2f, 72, 8));
        assertEquals("downward from the last seat stays at the last seat", 800,
                SlothSeats.choose(seats, 800, 1.2f, 72, 8));
        assertEquals("upward from full moves one seat down, not to the peek", 400,
                SlothSeats.choose(seats, 800, -1.2f, 72, 8));
        assertEquals("upward from the fitted seat closes, skipping the peek", 0,
                SlothSeats.choose(seats, 400, -1.2f, 72, 8));
        assertEquals("upward from the peek closes", 0,
                SlothSeats.choose(seats, 72, -1.2f, 72, 8));
    }

    /** A slow release settles nearest, projected a little in the direction of travel. */
    @Test
    public void aSlowReleaseSettlesNearest() {
        int[] seats = {0, 72, 400, 800};
        assertEquals("just under the peek, easing up: the peek", 72,
                SlothSeats.choose(seats, 60, 0.1f, 72, 8));
        assertEquals("just over the fitted seat, easing down: the fitted seat", 400,
                SlothSeats.choose(seats, 380, 0.2f, 72, 8));
        assertEquals("release near the shut end shuts", 0,
                SlothSeats.choose(seats, 30, 0f, 72, 8));
        assertEquals("a still release on a seat stays on it", 400,
                SlothSeats.choose(seats, 400, 0f, 72, 8));
        assertEquals("projection can carry it past the nearest seat", 800,
                SlothSeats.choose(seats, 700, 0.5f, 72, 8));
    }

    /** One seat only: the arithmetic still answers something sane. */
    @Test
    public void oneSeatIsNotADivisionByZero() {
        assertEquals(0, SlothSeats.choose(new int[]{0}, 0, 0f, 0, 8));
        assertEquals(0, SlothSeats.choose(new int[]{0}, 0, 2f, 0, 8));
        assertEquals(5, SlothSeats.choose(new int[]{}, 5, 2f, 0, 8));
    }

    private View findByViewTag(View v, String tag) {
        if (tag.equals(v.getTag())) {
            return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View found = findByViewTag(g.getChildAt(i), tag);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Views are found by tag: the shell restructures, the tests should not care. */
    private View drawerOf(Activity activity) {
        View tagged = findByViewTag(overlayOf(activity), "drawer");
        assertNotNull("drawer", tagged);
        return tagged;
    }

    private ViewGroup overlayOf(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        assertEquals("the shell adds itself on top of the app's own view", 2, content.getChildCount());
        return (ViewGroup) content.getChildAt(content.getChildCount() - 1);
    }

    /** BarUnion builds its buttons and tags them; the shell must find them that way. */
    private View tagged(TestActivity activity, String tag) {
        View v = new View(activity);
        v.setTag(tag);
        return v;
    }

    @Test
    public void shellBuildsItsFourSurfaces() {
        TestActivity activity = build();
        SlothShell.own(activity);

        ViewGroup overlay = overlayOf(activity);
        assertNotNull("one surface", findByViewTag(overlay, "surface"));

        View bar = findByViewTag(overlay, "bar");
        assertEquals("capsule sits at the top", android.widget.LinearLayout.LayoutParams.class, bar.getLayoutParams().getClass());
        // the bar fills its drag wrapper, which is the 56dp touch target
        View wrap = findByViewTag(overlay, "drag");
        assertEquals("bar is 56dp tall", 56 * activity.getResources().getDisplayMetrics().density,
                wrap.getLayoutParams().height, 0.5f);
    }

    @Test
    public void drawerStartsClosedAndShowsNothing() {
        TestActivity activity = build();
        SlothShell.own(activity);

        View drawer = drawerOf(activity);
        assertEquals("drawer height starts at zero", 0, drawer.getLayoutParams().height);
        assertEquals("and draws nothing", View.GONE, drawer.getVisibility());
    }

    @Test
    public void tappingTheHandleOpensTheDrawer() {
        TestActivity activity = build();
        SlothShell.own(activity);
        View drawer = drawerOf(activity);
        View handle = findByViewTag(overlayOf(activity), "drag");

        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 500f, 0);
        MotionEvent up = MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, 200f, 500f, 0);
        boolean downR = handle.dispatchTouchEvent(down);
        boolean upR = handle.dispatchTouchEvent(up);
        System.out.println("[dbg] down=" + downR + " up=" + upR
                + " vis=" + drawer.getVisibility() + " h=" + drawer.getLayoutParams().height
                + " listener=" + (handle.hasOnClickListeners() || handle.isClickable()));
        settle();
        System.out.println("[dbg] after settle vis=" + drawer.getVisibility()
                + " h=" + drawer.getLayoutParams().height);

        assertTrue("a tap opens it to pocket height", drawer.getLayoutParams().height > 0);
        assertEquals(View.VISIBLE, drawer.getVisibility());

        // tapping again closes it
        MotionEvent down2 = MotionEvent.obtain(t, t + 900, MotionEvent.ACTION_DOWN, 200f, 500f, 0);
        MotionEvent up2 = MotionEvent.obtain(t, t + 940, MotionEvent.ACTION_UP, 200f, 500f, 0);
        handle.dispatchTouchEvent(down2);
        handle.dispatchTouchEvent(up2);
        settle();
        assertEquals("a second tap closes it", 0, drawer.getLayoutParams().height);
    }

    @Test
    public void addressMirrorsTheRealOmnibox() {
        TestActivity activity = build();
        activity.binding.urlBar.setText("https://example.org/page");
        SlothShell.own(activity);

        EditText shown = findAddress(overlayOf(activity));
        assertNotNull("the bar owns an address field", shown);
        assertEquals("https://example.org/page", shown.getText().toString());
    }

    @Test
    public void typedAddressGoesThroughGo() {
        TestActivity activity = build();
        SlothShell.own(activity);

        EditText shown = findAddress(overlayOf(activity));
        shown.setText("sloth.example");
        DrawerFace.last = null;
        shown.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_GO);

        // the browser's own routine is private; a public-only lookup is what made typing an
        // address do nothing on the device while this harness stayed green
        assertEquals("sloth.example", DrawerFace.last);
    }

    @Test
    public void capsuleButtonsReachTheOriginalWidgets() {
        TestActivity activity = build();
        Counter back = new Counter();
        Counter plus = new Counter();
        activity.binding.btnBack.setOnClickListener(back);
        activity.binding.btnPlus.setOnClickListener(plus);

        SlothShell.own(activity);

        View bar = findByViewTag(overlayOf(activity), "bar");
        ViewGroup row = (ViewGroup) bar;
        row.getChildAt(0).performClick();  // back glyph
        row.getChildAt(row.getChildCount() - 1).performClick();  // menu glyph

        assertEquals("back forwards to the browser's own button", 1, back.clicks);
    }

    @Test
    public void shellCollapsesTheOriginalRowWithoutDeletingIt() {
        TestActivity activity = build();
        SlothShell.own(activity);

        assertEquals("dock hidden", View.GONE, activity.binding.chromeDock.getVisibility());
        assertEquals("dock height zeroed", 0, activity.binding.chromeDock.getLayoutParams().height);
        assertEquals("dock margins cleared", 0,
                ((ViewGroup.MarginLayoutParams) activity.binding.chromeDock.getLayoutParams()).topMargin);
        assertEquals("row still in the tree to repaint", 1,
                activity.binding.chromeDock.getChildCount());
    }

    @Test
    public void tabsAreMirroredFromTheBrowsersOwnStrip() {
        TestActivity activity = build();
        LinearLayout strip = activity.binding.slotTabs;
        for (String title : new String[]{"Home", "Docs", "News"}) {
            View chip = new View(activity);
            chip.setContentDescription(title);
            strip.addView(chip);
        }
        SlothShell.own(activity);

        View tabRow = findTabRow(drawerOf(activity));
        assertNotNull(tabRow);
        assertEquals("three tabs mirrored", 3, ((ViewGroup) tabRow).getChildCount());
        assertEquals("titles carried over", "Home", tabLabel(((ViewGroup) tabRow).getChildAt(0)));
    }

    /**
     * The drawer animation is driven by wall-clock time and 16ms posts, so the
     * looper has to be pumped while real time passes for it to finish.
     */
    /** Frames are posted 16 ms apart, so the clock has to move, not just the queue. */
    private void settle() {
        org.robolectric.shadows.ShadowLooper.getShadowMainLooper()
                .idleFor(java.time.Duration.ofSeconds(2));
    }

    @Test
    public void tabTitlesRefreshWhenAPageFinishesLoading() {
        TestActivity activity = build();
        android.widget.TextView chip = new android.widget.TextView(activity);
        chip.setText("Loading...");
        activity.binding.slotTabs.addView(chip);
        SlothShell.own(activity);

        View tabRow = findTabRow(drawerOf(activity));
        assertNotNull(tabRow);
        assertEquals("initial title mirrored", "Loading...", tabLabel(((ViewGroup) tabRow).getChildAt(0)));

        // the page finishes loading under the same tab
        chip.setText("Sloth Browser");
        SlothShell.own(activity);

        View refreshed = findTabRow(drawerOf(activity));
        assertNotNull(refreshed);
        assertEquals("same tab, new title, mirror updated", "Sloth Browser",
                tabLabel(((ViewGroup) refreshed).getChildAt(0)));
    }

    @Test
    public void aSecondActivityGetsItsOwnShell() {
        TestActivity first = build();
        SlothShell.own(first);
        ViewGroup firstOverlay = overlayOf(first);
        assertNotNull("one surface", findByViewTag(firstOverlay, "surface"));

        // rotation / relaunch: new activity, statics still pointing at the old one
        TestActivity second = build();
        SlothShell.own(second);

        assertEquals("the new activity carries the shell", 2,
                ((ViewGroup) second.findViewById(android.R.id.content)).getChildCount());
        assertNotEquals("and it is a different overlay", firstOverlay, overlayOf(second));
        assertNotNull("the old one is left alone",
                findByViewTag(firstOverlay, "surface"));
        assertTrue("the live activity is the one the shell answers for",
                SlothShell.ownsActivity(second));
        assertFalse("and it has let go of the finished one", SlothShell.ownsActivity(first));
    }

    /* ------------------------------------------------------------- dashboard */

    @Test
    public void dashboardIsInTheDrawerAndSaysWhatItIs() {
        TestActivity activity = build();
        SlothShell.own(activity);
        View drawer = drawerOf(activity);

        // the bar above is the dashboard's header, so the panel does not repeat a masthead
        assertNull("no second masthead", findChip(drawer, "DASHBOARD"));
        assertNull("and no wordmark of its own", findChip(drawer, "Sloth"));
        assertNotNull("the tabs are the first thing in it", findChip(drawer, "Tabs"));
        assertNotNull("with their add control", findTagged(drawer, "New tab"));
        assertNotNull("widget section", findChip(drawer, "WIDGETS"));
        assertNotNull("clock widget", findChip(drawer, "CLOCK"));
        assertNotNull("notes widget", findChip(drawer, "NOTES"));
        assertNotNull("calculator widget", findChip(drawer, "CALC"));
    }

    @Test
    public void everyDashboardControlIsLabelledForScreenReaders() {
        TestActivity activity = build();
        SlothShell.own(activity);
        int[] unlabelled = new int[1];
        walk(drawerOf(activity), new ViewVisitor() {
            @Override
            public void visit(View v) {
                if (v.isClickable() && v.getContentDescription() == null
                        && !(v instanceof EditText)) {
                    unlabelled[0]++;
                    System.out.println("[a11y] unlabelled: " + v.getClass().getSimpleName()
                            + " child# " + (v.getParent() instanceof ViewGroup
                                ? ((ViewGroup) v.getParent()).indexOfChild(v) : -1));
                }
            }
        });
        assertEquals("clickable controls with no spoken label", 0, unlabelled[0]);
    }

    @Test
    public void editModeStatesItself() {
        TestActivity activity = build();
        SlothShell.own(activity);
        View drawer = drawerOf(activity);

        View edit = findChip(drawer, "Edit widgets");
        assertNotNull(edit);
        tapChip(drawer, "Edit widgets");
        settle();

        // one door: editing opens the same chooser as Add widget
        View chooser = findByViewTag(drawerOf(activity), "widgets");
        assertNotNull("editing opens the widget chooser", chooser);
        assertEquals(View.VISIBLE, chooser.getVisibility());
    }

    @Test
    public void dashboardActionsReachTheBrowser() {
        TestActivity activity = build();
        Counter plus = new Counter();
        Counter home = new Counter();
        activity.binding.btnPlus.setOnClickListener(plus);
        activity.binding.btnHome.setOnClickListener(home);

        SlothShell.own(activity);
        View drawer = drawerOf(activity);

        tapTagged(drawer, "New tab");
        tapChip(drawer, "Home");

        assertEquals("new tab forwarded", 1, plus.clicks);
        assertEquals("home forwarded", 1, home.clicks);
    }

    @Test
    public void notesSurviveRelaunch() {
        TestActivity activity = build();
        SlothShell.own(activity);
        View drawer = drawerOf(activity);

        EditText note = findNoteField(drawer);
        assertNotNull("notes field", note);
        note.setText("milk, then the bar");
        org.robolectric.shadows.ShadowLooper.idleMainLooper();

        assertEquals("milk, then the bar",
                activity.getSharedPreferences("slothworks", android.content.Context.MODE_PRIVATE)
                        .getString("note", ""));
    }

    @Test
    public void historyButtonsFadeWhenThereIsNowhereToGo() {
        TestActivity activity = build();
        activity.binding.btnBack.setEnabled(false);
        activity.binding.btnFwd.setEnabled(true);
        SlothShell.own(activity);

        View bar = findByViewTag(overlayOf(activity), "bar");
        ViewGroup row = (ViewGroup) bar;
        assertEquals("back dimmed", 0.42f, row.getChildAt(0).getAlpha(), 0.01f);
        assertEquals("forward full", 1f, row.getChildAt(1).getAlpha(), 0.01f);
    }

    private interface ViewVisitor {
        void visit(View v);
    }

    private void walk(View v, ViewVisitor visitor) {
        visitor.visit(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                walk(g.getChildAt(i), visitor);
            }
        }
    }

    /** A tap anywhere on a card must reach the card, not the label inside it. */
    private void tapTagged(View root, String description) {
        View v = findTagged(root, description);
        assertNotNull(description, v);
        View tappable = v;
        while (tappable != null && !tappable.isClickable()) {
            tappable = tappable.getParent() instanceof View ? (View) tappable.getParent() : null;
        }
        assertNotNull(description, tappable);
        tappable.performClick();
    }

    private void tapChip(View root, String label) {
        // the same words can appear twice - a page title and a button - so take the one
        // that actually sits in something tappable
        java.util.List<View> matches = new java.util.ArrayList<>();
        collectChips(root, label, matches, true);
        if (matches.isEmpty()) {
            // a closed drawer's buttons are still the buttons we mean; only prefer shown ones
            collectChips(root, label, matches, false);
        }
        assertFalse(label + " in DRAWER[" + dumpText(root) + "] CHOOSER=" + SlothShell.sheetStateForTest(), matches.isEmpty());
        View target = null;
        for (View found : matches) {
            View walk = found;
            while (walk != null && !walk.isClickable()) {
                walk = walk.getParent() instanceof View ? (View) walk.getParent() : null;
            }
            if (walk != null) {
                target = walk;
                break;
            }
        }
        assertNotNull("a clickable card behind " + label, target);
        target.performClick();
    }

    private void tapByDescription(View root, String description) {
        View found = byDescription(root, description);
        assertNotNull(description, found);
        View walk = found;
        while (walk != null && !walk.isClickable()) {
            walk = walk.getParent() instanceof View ? (View) walk.getParent() : null;
        }
        assertNotNull("a clickable card behind " + description, walk);
        walk.performClick();
    }

    private static View byDescription(View v, String description) {
        CharSequence described = v.getContentDescription();
        if (described != null && description.contentEquals(described)) {
            return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View found = byDescription(g.getChildAt(i), description);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void collectChips(View v, String label, java.util.List<View> out,
                                     boolean shownOnly) {
        CharSequence text = v instanceof TextView ? ((TextView) v).getText() : null;
        if (text != null && label.contentEquals(text) && (!shownOnly || v.isShown())) {
            out.add(v);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                collectChips(g.getChildAt(i), label, out, shownOnly);
            }
        }
    }

    private EditText findNoteField(View v) {
        if (v instanceof EditText && "Notes".equals(String.valueOf(v.getContentDescription()))) {
            return (EditText) v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                EditText found = findNoteField(g.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private View findTagged(View v, String description) {
        if (description.equals(String.valueOf(v.getContentDescription()))) {
            return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View found = findTagged(g.getChildAt(i), description);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    public void thePageStartsBelowTheBar() {
        TestActivity activity = build();
        int before = activity.binding.chromeDock.getPaddingTop();
        SlothShell.own(activity);

        View appRoot = ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
        int pad = appRoot.getPaddingTop();
        // the surface carries the system inset; the page clears both it and the bar
        View surface = findByViewTag(overlayOf(activity), "surface");
        int inset = surface.getPaddingTop() - px(8);
        assertEquals("page pushed down by the bar height plus the system inset",
                px(64) + inset, pad);
    }

    @Test
    public void thePadlockSaysInsecureInWordsAndShape() {
        TestActivity activity = build();
        SlothShell.own(activity);
        EditText shown = findAddress(overlayOf(activity));
        assertNotNull(shown);

        shown.setText("http://not-secure.example");
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertEquals("warns in colour", 0xFFE2A05A, ((SlothIcons) findLock(overlayOf(activity))).colour());
        assertTrue("and says so in words",
                String.valueOf(findLock(overlayOf(activity)).getContentDescription())
                        .contains("not a secure address"));

        shown.setText("https://sloth.example");
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertEquals("ink when secure", SlothTheme.dim(),
                ((SlothIcons) findLock(overlayOf(activity))).colour());
        assertTrue("and it is the way into site settings",
                String.valueOf(findLock(overlayOf(activity)).getContentDescription())
                        .startsWith("Site settings"));
    }

    private View findLock(View root) {
        final View[] hit = new View[1];
        walk(root, new ViewVisitor() {
            @Override
            public void visit(View v) {
                Object description = v.getContentDescription();
                if (description != null && description.toString().startsWith("Site settings")) {
                    hit[0] = v;
                }
            }
        });
        return hit[0];
    }

    @Test
    public void theTabCountIsVisibleAndOpensTheDrawer() {
        TestActivity activity = build();
        SlothShell.own(activity);

        View bar = findByViewTag(overlayOf(activity), "bar");
        View counter = findChip(bar, "3");
        assertNotNull("counter shows the browser's own count", counter);

        tapChip(bar, "3");
        settle();
        assertTrue("tapping it opens the drawer",
                drawerOf(activity).getLayoutParams().height > 0);
    }

    @Test
    public void theAddressPadlockOpensSiteInfo() {
        TestActivity activity = build();
        Counter info = new Counter();
        activity.binding.pageInfo.setOnClickListener(info);
        SlothShell.own(activity);
        findAddress(overlayOf(activity)).setText("https://sloth.example/leaf");
        org.robolectric.shadows.ShadowLooper.idleMainLooper();

        View lock = findLock(overlayOf(activity));
        assertNotNull(lock);
        assertTrue("the padlock is a control, not decoration", lock.isClickable());
        lock.performClick();
        assertEquals("site info asked for", 1, info.clicks);
    }

    @Test
    public void tabsCanBeClosedFromTheDrawer() {
        TestActivity activity = build();
        View close = tagged(activity, "close");
        Counter closed = new Counter();
        close.setOnClickListener(closed);
        android.widget.FrameLayout chip = new android.widget.FrameLayout(activity);
        android.widget.TextView title = new android.widget.TextView(activity);
        title.setText("Docs");
        chip.addView(title);
        chip.addView(close);
        activity.binding.slotTabs.addView(chip);
        SlothShell.own(activity);

        View drawer = drawerOf(activity);
        View closeButton = findChip(drawer, "×");
        assertNotNull("a tab card offers a close button", closeButton);
        closeButton.performClick();
        assertEquals("and it closes the browser's tab", 1, closed.clicks);
    }

    @Test
    public void tappingThePageBehindTheDrawerClosesIt() {
        TestActivity activity = build();
        SlothShell.own(activity);

        View handle = findByViewTag(overlayOf(activity), "drag");
        long t = SystemClock.uptimeMillis();
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 500f, 0));
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, 200f, 500f, 0));
        settle();
        assertTrue("open", drawerOf(activity).getLayoutParams().height > 0);

        // there is no scrim any more: the page itself dims, and a tap on bare bar closes it
        View page = ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
        assertTrue("the page dims behind it", page.getAlpha() < 1f);
        View bar = findByViewTag(overlayOf(activity), "drag");
        long t2 = SystemClock.uptimeMillis();
        bar.dispatchTouchEvent(MotionEvent.obtain(t2, t2, MotionEvent.ACTION_DOWN, 300f, 40f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(t2, t2 + 40, MotionEvent.ACTION_UP, 300f, 40f, 0));
        settle();
        assertEquals("and a tap on the bar puts it away", 0, drawerOf(activity).getLayoutParams().height);
    }

    @Test
    public void reloadOffersStopOnLongPress() {
        TestActivity activity = build();
        SlothShell.own(activity);

        final View[] reload = new View[1];
        walk(findByViewTag(overlayOf(activity), "surface"), new ViewVisitor() {
            @Override
            public void visit(View v) {
                if ("Reload".equals(String.valueOf(v.getContentDescription()))) {
                    reload[0] = v;
                }
            }
        });
        assertNotNull("reload control", reload[0]);
        assertTrue("a load can be stopped from the bar", reload[0].isLongClickable());
        // in the app this calls BarUnion.stop(); the harness has no BarUnion class, so
        // the listener reports false rather than pretending the load stopped
        reload[0].performLongClick();
    }

    @Test
    public void swipingTheAddressMovesAlongTheTabs() {
        TestActivity activity = build();
        android.widget.FrameLayout first = new android.widget.FrameLayout(activity);
        android.widget.FrameLayout second = new android.widget.FrameLayout(activity);
        first.setSelected(true);
        Counter firstClicks = new Counter();
        Counter secondClicks = new Counter();
        first.setOnClickListener(firstClicks);
        second.setOnClickListener(secondClicks);
        activity.binding.slotTabs.addView(first);
        activity.binding.slotTabs.addView(second);

        SlothShell.own(activity);
        View pill = findAddress(overlayOf(activity)).getParent() instanceof View
                ? (View) findAddress(overlayOf(activity)).getParent() : null;
        assertNotNull(pill);

        long t = SystemClock.uptimeMillis();
        pill.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 20f, 0));
        pill.dispatchTouchEvent(MotionEvent.obtain(t, t + 30, MotionEvent.ACTION_MOVE, 60f, 22f, 0));
        pill.dispatchTouchEvent(MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, 60f, 22f, 0));

        assertEquals("swipe left moved to the next tab", 1, secondClicks.clicks);
        assertEquals("and left the current one alone", 0, firstClicks.clicks);
    }

    @Test
    public void pocketHeightHidesTheShelfAndTallerShowsIt() {
        TestActivity activity = build();
        SlothShell.own(activity);
        View drawer = drawerOf(activity);

        View handle = findByViewTag(overlayOf(activity), "drag");
        long t = SystemClock.uptimeMillis();
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 500f, 0));
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, 200f, 500f, 0));
        settle();

        View shelf = findChip(drawer, "CLOCK");
        assertNotNull("clock is in the shelf", shelf);
        // the shelf is hidden as a container; its children keep their own flags
        assertTrue("pocket height shows tabs, not the shelf", !isEffectivelyVisible(shelf));
    }

    @Test
    public void choosingATabPutsTheDrawerAway() {
        TestActivity activity = build();
        View chip = new View(activity);
        activity.binding.slotTabs.addView(chip);
        SlothShell.own(activity);

        View handle = findByViewTag(overlayOf(activity), "drag");
        long t = SystemClock.uptimeMillis();
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 500f, 0));
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, 200f, 500f, 0));
        settle();

        View tabRow = findTabRow(drawerOf(activity));
        assertNotNull(tabRow);
        ((ViewGroup) tabRow).getChildAt(0).performClick();
        settle();
        assertEquals("the drawer closes once you have chosen", 0,
                drawerOf(activity).getLayoutParams().height);
    }

    @Test
    public void reducedMotionSkipsTheAnimation() {
        TestActivity activity = build();
        android.provider.Settings.Global.putFloat(activity.getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        SlothShell.own(activity);

        View handle = findByViewTag(overlayOf(activity), "drag");
        long t = SystemClock.uptimeMillis();
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 500f, 0));
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, 200f, 500f, 0));

        assertTrue("it is open immediately, with no frames to wait for",
                drawerOf(activity).getLayoutParams().height > 0);
    }

    private void press(View drawer, String spoken) {
        View v = findTagged(drawer, spoken);
        assertNotNull(spoken, v);
        v.performClick();
    }

    private android.widget.ScrollView findScroller(View v) {
        if (v instanceof android.widget.ScrollView) {
            return (android.widget.ScrollView) v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                android.widget.ScrollView found = findScroller(g.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private String dumpText(View root) {
        final StringBuilder out = new StringBuilder();
        walk(root, new ViewVisitor() {
            @Override
            public void visit(View v) {
                if (v instanceof TextView) {
                    out.append(((TextView) v).getText()).append('|');
                }
            }
        });
        return out.toString();
    }

    private int heightOf(View drawer) {
        return ((ViewGroup.LayoutParams) drawer.getLayoutParams()).height;
    }

    private int px(int dp) {
        return Math.round(dp * RuntimeEnvironment.getApplication().getResources().getDisplayMetrics().density);
    }

    /** GONE on a parent hides everything inside it, whatever the child's own flag says. */
    private boolean isEffectivelyVisible(View v) {
        View walk = v;
        while (walk != null) {
            if (walk.getVisibility() != View.VISIBLE) {
                return false;
            }
            walk = walk.getParent() instanceof View ? (View) walk.getParent() : null;
        }
        return true;
    }

    @Test
    public void thereIsNoWayBackToTheOldBar() {
        TestActivity activity = build();
        SlothShell.own(activity);

        assertNull("the drawer no longer offers the old bar", findChip(drawerOf(activity), "Original bar"));
        assertNull("and no floating pill switches back either", findTagged(overlayOf(activity), "New bar"));

        // even if the app repaints its own chrome, it does not come back
        activity.binding.chromeDock.setVisibility(View.VISIBLE);
        SlothShell.own(activity);
        assertEquals(View.GONE, activity.binding.chromeDock.getVisibility());
    }

    @Test
    public void aStalePreferenceCannotResurrectTheOldBar() {
        TestActivity activity = build();
        activity.getSharedPreferences("slothworks", Context.MODE_PRIVATE)
                .edit().putBoolean("originalBar", true).apply();

        SlothShell.own(activity);

        assertEquals("the old row stays gone", View.GONE, activity.binding.chromeDock.getVisibility());
        assertEquals("and the preference is cleared", false,
                activity.getSharedPreferences("slothworks", Context.MODE_PRIVATE)
                        .getBoolean("originalBar", false));
    }

    @Test
    public void theOldChromeAndItsDrawerAreGoneNotJustCovered() {
        TestActivity activity = build();
        SlothShell.own(activity);

        assertEquals("old row", View.GONE, activity.binding.chromeDock.getVisibility());
        assertEquals("old inner row", View.GONE, activity.binding.chromeMain.getVisibility());
        assertEquals("old grip", View.GONE, activity.binding.chromeGrip.getVisibility());
        assertEquals("old tool row", View.GONE, activity.binding.chromeToolRow.getVisibility());
        assertEquals("old favourites", View.GONE, activity.binding.chromeFavRow.getVisibility());
        assertEquals("old drawer panel", View.GONE, activity.binding.panel.getVisibility());
        assertEquals("old drawer body", View.GONE, activity.binding.panelBody.getVisibility());
    }

    @Test
    public void theOldDrawerStaysGoneWhenTheAppRepaintsIt() {
        TestActivity activity = build();
        SlothShell.own(activity);

        // DrawerFace stages its panel on every chrome pass
        activity.binding.panel.setVisibility(View.VISIBLE);
        SlothShell.own(activity);
        assertEquals("re-asserted on the next pass", View.GONE, activity.binding.panel.getVisibility());
    }

    @Test
    public void aFindFieldAppearsOnceThereAreManyTabsAndFiltersThem() {
        TestActivity activity = build();
        for (String title : new String[]{"Docs", "News", "Shop", "Mail", "Maps", "Music", "Bank", "Wiki"}) {
            android.widget.TextView chip = new android.widget.TextView(activity);
            chip.setText(title);
            activity.binding.slotTabs.addView(chip);
        }
        SlothShell.own(activity);
        View drawer = drawerOf(activity);

        android.widget.EditText find = (android.widget.EditText) findTagged(drawer, "Find a tab");
        assertNotNull("eight tabs earn a find field", find);
        assertEquals(View.VISIBLE, find.getVisibility());

        find.setText("do");
        org.robolectric.shadows.ShadowLooper.idleMainLooper();

        View tabRow = findTabRow(drawer);
        assertNotNull(tabRow);
        int shown = 0;
        for (int i = 0; i < ((ViewGroup) tabRow).getChildCount(); i++) {
            if (((ViewGroup) tabRow).getChildAt(i).getVisibility() == View.VISIBLE) {
                shown++;
            }
        }
        assertEquals("only the match survives the filter", 1, shown);
    }

    @Test
    public void aFewTabsDoNotGetAFindField() {
        TestActivity activity = build();
        android.widget.TextView chip = new android.widget.TextView(activity);
        chip.setText("Docs");
        activity.binding.slotTabs.addView(chip);
        SlothShell.own(activity);

        View find = findTagged(drawerOf(activity), "Find a tab");
        assertNotNull(find);
        assertEquals("hidden until it is needed", View.GONE, find.getVisibility());
    }

    @Test
    public void theDrawerPushesThePageInsteadOfCoveringIt() {
        TestActivity activity = build();
        SlothShell.own(activity);
        View appRoot = ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
        int resting = appRoot.getPaddingTop();

        View handle = findByViewTag(overlayOf(activity), "drag");
        long t = SystemClock.uptimeMillis();
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 500f, 0));
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, 200f, 500f, 0));
        settle();

        int opened = appRoot.getPaddingTop();
        assertEquals("the page moved down by exactly the drawer's height",
                resting + drawerOf(activity).getLayoutParams().height, opened);
        assertTrue("which means the drawer did open", opened > resting);
    }

    @Test
    public void longPressingTheAddressCopiesIt() {
        TestActivity activity = build();
        SlothShell.own(activity);
        android.widget.EditText field = findAddress(overlayOf(activity));
        field.setText("sloth.example/page");

        assertTrue("long press is handled", field.performLongClick());

        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        assertEquals("the address is on the clipboard",
                "sloth.example/page", clipboard.getPrimaryClip().getItemAt(0).getText().toString());
    }

    @Test
    public void theDrawerOpensAtTheSizeItWasLeftAt() {
        TestActivity activity = build();
        activity.getSharedPreferences("slothworks", Context.MODE_PRIVATE)
                .edit().putBoolean("drawerExpanded", true).apply();
        SlothShell.own(activity);

        View tabsChip = findTagged(overlayOf(activity), "Tabs");
        assertNotNull(tabsChip);
        tabsChip.performClick();
        settle();

        int peek = px(230);
        assertTrue("it opened taller than the peek, remembering the last size: "
                        + drawerOf(activity).getLayoutParams().height + " vs " + peek,
                drawerOf(activity).getLayoutParams().height > peek + px(24));
    }

    @Test
    public void holdingTheTabCountReachesThePrivateSwitcher() {
        TestActivity activity = build();
        SlothShell.own(activity);

        View chip = findTagged(overlayOf(activity), "Tabs");
        assertNotNull(chip);
        chip.performLongClick();

        assertEquals("the switcher was called", 1, activity.switcherCalls);
    }

    @Test
    public void leavingSettingsPutsTheOldSurfaceAwayAgain() {
        TestActivity activity = build();
        SlothShell.own(activity);
        openBrowserPanel(activity);
        // the app's own panel used to be lent out for this; it stays shut now, because an
        // empty panel over the page is exactly the box that kept being reported
        assertEquals("the app's panel is not lent", View.GONE,
                activity.binding.panel.getVisibility());
        assertEquals("our page is what opens", View.VISIBLE,
                findByViewTag(overlayOf(activity), "settings-page").getVisibility());

        // reopening the drawer means the errand moved on
        View handle = findByViewTag(overlayOf(activity), "drag");
        long t = SystemClock.uptimeMillis();
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 500f, 0));
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, 200f, 500f, 0));
        settle();

        assertEquals("the old panel is hidden again", View.GONE,
                activity.binding.panel.getVisibility());
    }

    @Test
    public void theShelfDoesNotSendYouToTheDrawerWeRemoved() {
        TestActivity activity = build();
        SlothShell.own(activity);

        for (String gone : new String[]{"Passwords", "Links"}) {
            assertNull(gone + " is no longer offered", findChip(drawerOf(activity), gone));
        }
        assertNotNull("the calculator lives here instead", findChip(drawerOf(activity), "CALC"));
        assertNotNull(findChip(drawerOf(activity), "NOTES"));
    }

    @Test
    public void theDrawerCanStartANewTabAndThenGetsOutOfTheWay() {
        TestActivity activity = build();
        SlothShell.own(activity);

        View handle = findByViewTag(overlayOf(activity), "drag");
        long t = SystemClock.uptimeMillis();
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 500f, 0));
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, 200f, 500f, 0));
        settle();

        Counter plus = new Counter();
        activity.binding.btnPlus.setOnClickListener(plus);

        tapByDescription(drawerOf(activity), "New tab");
        settle();

        assertEquals("the app's own new-tab control ran", 1, plus.clicks);
        assertEquals("and the drawer closed", 0, drawerOf(activity).getLayoutParams().height);
    }

    @Test
    public void theDrawerHeadSaysWhereThePageIs() {
        TestActivity activity = build();
        ((android.widget.EditText) activity.binding.urlBar).setText("sloth.example/page");
        SlothShell.own(activity);

        View label = findChip(drawerOf(activity), "sloth.example/page");
        assertNotNull("the address is on show", label);
    }

    @Test
    public void theDrawerHeadSaysWhenNothingIsOpen() {
        TestActivity activity = build();
        SlothShell.own(activity);
        assertNotNull(findChip(drawerOf(activity), "No page open"));
    }

    @Test
    public void theOldPullToOpenDrawerGestureIsReleased() {
        TestActivity activity = build();
        // the app tags the page container and binds its own pull gesture to it
        View page = new View(activity);
        page.setTag("sloth-pull");
        page.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                return true;
            }
        });
        page.setClickable(true);
        ((ViewGroup) activity.findViewById(android.R.id.content)).addView(page);

        SlothShell.own(activity);

        assertNull("the tag is gone", ((View) page).getTag());
        assertFalse("the old gesture is not eating swipes", ((View) page).isClickable());
    }

    @Test
    public void theDrawerContentSettlesAndLeavesNoResidue() {
        TestActivity activity = build();
        SlothShell.own(activity);

        View handle = findByViewTag(overlayOf(activity), "drag");
        long t = SystemClock.uptimeMillis();
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 500f, 0));
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, 200f, 500f, 0));

        // mid-flight: it is somewhere between hidden and settled
        org.robolectric.shadows.ShadowLooper.getShadowMainLooper()
                .idleFor(java.time.Duration.ofMillis(48));
        assertTrue("it starts slightly transparent", drawerOf(activity).getAlpha() < 1f);

        settle();
        assertEquals("and ends fully opaque", 1f, drawerOf(activity).getAlpha(), 0.001f);
        assertEquals("with no offset left", 0f, drawerOf(activity).getTranslationY(), 0.001f);
    }

    @Test
    public void reducedMotionLeavesTheDrawerContentFlat() {
        TestActivity activity = build();
        android.provider.Settings.Global.putFloat(activity.getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        SlothShell.own(activity);

        View tabsChip = findTagged(overlayOf(activity), "Tabs");
        tabsChip.performClick();

        assertEquals(1f, drawerOf(activity).getAlpha(), 0.001f);
        assertEquals(0f, drawerOf(activity).getTranslationY(), 0.001f);
    }

    @Test
    public void theCurrentTabIsMarkedAndSaysSo() {
        TestActivity activity = build();
        android.widget.TextView first = new android.widget.TextView(activity);
        first.setText("Docs");
        android.widget.TextView second = new android.widget.TextView(activity);
        second.setText("News");
        second.setSelected(true);
        activity.binding.slotTabs.addView(first);
        activity.binding.slotTabs.addView(second);

        SlothShell.own(activity);
        View tabRow = findTabRow(drawerOf(activity));
        assertNotNull(tabRow);

        View currentCard = ((ViewGroup) tabRow).getChildAt(1);
        View otherCard = ((ViewGroup) tabRow).getChildAt(0);
        assertTrue("the current tab says so",
                String.valueOf(currentCard.getContentDescription()).contains("current tab"));
        assertFalse("the other one does not",
                String.valueOf(otherCard.getContentDescription()).contains("current tab"));
        assertNotEquals("and they do not look the same",
                otherCard.getBackground(), currentCard.getBackground());
    }

    @Test
    public void withNoActiveTabNothingIsMarked() {
        TestActivity activity = build();
        android.widget.TextView chip = new android.widget.TextView(activity);
        chip.setText("Docs");
        activity.binding.slotTabs.addView(chip);
        SlothShell.own(activity);

        View tabRow = findTabRow(drawerOf(activity));
        assertFalse("no chip claims selection, so no card is marked",
                String.valueOf(((ViewGroup) tabRow).getChildAt(0).getContentDescription())
                        .contains("current tab"));
    }

    @Test
    public void theShellStartsBelowTheSystemStatusBar() {
        TestActivity activity = build();
        org.robolectric.Robolectric.buildActivity(TestActivity.class);
        int inset = px(24); // Robolectric reports a 24dp status bar by default

        SlothShell.own(activity);

        View surface = findByViewTag(overlayOf(activity), "surface");
        int clearedBy = surface.getPaddingTop();
        assertTrue("the capsule clears the status bar: " + clearedBy, clearedBy >= inset);

        View appRoot = ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
        int pad = appRoot.getPaddingTop();
        assertTrue("and the page is pushed below both: " + pad, pad >= inset + px(64));
    }

    @Test
    public void theHandleIsBigEnoughToGrab() {
        TestActivity activity = build();
        SlothShell.own(activity);
        View bar = findByViewTag(overlayOf(activity), "drag");
        int height = bar.getLayoutParams().height;
        assertTrue("the whole bar is the target, not a hairline: " + height, height >= px(52));
    }

    @Test
    public void aFlickOpensTheDrawerAllTheWay() {
        TestActivity activity = build();
        SlothShell.own(activity);
        View bar = findByViewTag(overlayOf(activity), "drag");

        long t = SystemClock.uptimeMillis();
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 40f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t + 15, MotionEvent.ACTION_MOVE, 200f, 200f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t + 25, MotionEvent.ACTION_UP, 200f, 260f, 0));
        settle();

        assertTrue("a downward flick opens it rather than snapping back: "
                        + drawerOf(activity).getLayoutParams().height,
                drawerOf(activity).getLayoutParams().height > px(200));
    }

    @Test
    public void aPartialDrawerLeaveIsClearedInsteadOfShowingAnEmptyBand() {
        TestActivity activity = build();
        SlothShell.own(activity);

        View drawer = drawerOf(activity);
        // as if a drag ended without snapping: a sliver of sheet, no content
        drawer.setVisibility(View.VISIBLE);
        drawer.getLayoutParams().height = px(90);
        drawer.setLayoutParams(drawer.getLayoutParams());

        SlothShell.own(activity);

        assertEquals("a sliver below the smallest snap point is closed, not left showing",
                0, drawerOf(activity).getLayoutParams().height);
        assertEquals(View.GONE, drawerOf(activity).getVisibility());
    }

    @Test
    public void theDrawerOpensShowingItsTopNotTheBottom() {
        TestActivity activity = build();
        SlothShell.own(activity);

        // whatever happened last time, the scroll is not where the drawer starts
        View drawer = drawerOf(activity);
        android.widget.ScrollView scroller = findScroller(drawer);
        assertNotNull(scroller);
        scroller.scrollTo(0, 400);

        View tabsChip = findTagged(overlayOf(activity), "Tabs");
        tabsChip.performClick();
        settle();

        assertEquals("opening starts at the top of the drawer", 0, scroller.getScrollY());
        assertNotNull("which is where the head line is", findChip(drawer, "No page open"));
    }

    @Test
    public void holdingTheTabCountOffersTheAppsOwnSwitcher() {
        TestActivity activity = build();
        SlothShell.own(activity);

        View tabsChip = findTagged(overlayOf(activity), "Tabs");
        assertNotNull(tabsChip);
        assertTrue("the control is long-pressable", ((View) tabsChip).isLongClickable());
        // the app's switcher is not on this classpath; the press must not throw
        ((View) tabsChip).performLongClick();
    }

    @Test
    public void theCalculatorInTheDrawerActuallyAddsUp() {
        TestActivity activity = build();
        SlothShell.own(activity);
        View drawer = drawerOf(activity);

        View card = findChip(drawer, "CALC");
        assertNotNull(card);
        View tappable = card;
        while (tappable != null && !tappable.isClickable()) {
            tappable = tappable.getParent() instanceof View ? (View) tappable.getParent() : null;
        }
        assertNotNull(tappable);
        tappable.performClick();

        press(drawer, "Calculator 7");
        press(drawer, "Calculator +");
        press(drawer, "Calculator 3");
        press(drawer, "Calculator =");

        View face = findTagged(drawer, "Calculator shows 10");
        assertNotNull("seven plus three is ten", face);
    }

    @Test
    public void theCalculatorDividesWithoutBlowingUp() {
        TestActivity activity = build();
        SlothShell.own(activity);
        View drawer = drawerOf(activity);
        View card = findChip(drawer, "CALC");
        View tappable = card;
        while (tappable != null && !tappable.isClickable()) {
            tappable = tappable.getParent() instanceof View ? (View) tappable.getParent() : null;
        }
        tappable.performClick();

        press(drawer, "Calculator 8");
        press(drawer, "Calculator \u00F7");
        press(drawer, "Calculator 0");
        press(drawer, "Calculator =");

        assertNotNull("dividing by zero shows something rather than crashing",
                findTagged(drawer, "Calculator shows 0"));
    }

    @Test
    public void theBarMenuOpensTheShellsOwnDrawer() {
        TestActivity activity = build();
        SlothShell.own(activity);
        assertEquals("starts closed", 0, drawerOf(activity).getLayoutParams().height);

        View menu = findTagged(overlayOf(activity), "Menu");
        assertNotNull(menu);
        menu.performClick();
        settle();

        assertTrue("the three dots open our drawer", drawerOf(activity).getLayoutParams().height > 0);
        assertEquals("and the app's panel stays hidden", View.GONE,
                activity.binding.panel.getVisibility());
    }

    @Test
    public void theDashboardOffersOnlyActionsTheShellCanDoItself() {
        TestActivity activity = build();
        SlothShell.own(activity);
        View drawer = drawerOf(activity);

        assertNotNull(findChip(drawer, "Home"));
        assertNotNull(findChip(drawer, "Bookmarks"));
        assertNotNull(findChip(drawer, "History"));
        assertNotNull(findChip(drawer, "Downloads"));
        assertNull("add-ons would reopen the app drawer", findChip(drawer, "Add-ons"));
        assertNull("so would its menu", findChip(drawer, "Menu"));
    }

    @Test
    public void closeTabPressesTheTabsOwnCloseControl() {
        TestActivity activity = build();
        android.widget.TextView chip = new android.widget.TextView(activity);
        chip.setText("Docs");
        chip.setSelected(true);
        View closer = new View(activity);
        closer.setTag("close");
        Counter closes = new Counter();
        closer.setOnClickListener(closes);
        android.widget.FrameLayout chipRoot = new android.widget.FrameLayout(activity);
        chipRoot.addView(chip);
        chipRoot.addView(closer);
        activity.binding.slotTabs.addView(chipRoot);

        SlothShell.own(activity);
        View card = findChip(drawerOf(activity), "Docs");
        assertNotNull(card);
        View closeControl = findTagged(drawerOf(activity), "Close Docs");
        View current = card;
        while (closeControl == null && current != null) {
            closeControl = findTagged(current, "Close Docs");
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        assertNotNull("the tab card carries its own close control", closeControl);
        closeControl.performClick();

        assertEquals("the chip's own close control ran", 1, closes.clicks);
    }

    @Test
    public void theTallDrawerIsCappedToTheScreenNotToAMultipleOfThePeek() {
        TestActivity activity = build();
        SlothShell.own(activity);
        View bar = findByViewTag(overlayOf(activity), "drag");

        long t = SystemClock.uptimeMillis();
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 40f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t + 15, MotionEvent.ACTION_MOVE, 200f, 400f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t + 25, MotionEvent.ACTION_UP, 200f, 700f, 0));
        settle();

        int height = drawerOf(activity).getLayoutParams().height;
        int screen = activity.getResources().getDisplayMetrics().heightPixels;
        assertTrue("the drawer never covers the whole screen: " + height + " of " + screen,
                height > 0 && height <= Math.round(screen * 0.85f));
    }

    @Test
    public void theTabStripIsFoundByItsOwnTagWhenTheBindingIsWrong() {
        TestActivity activity = build();
        // on the phone the strip is tagged "tab-squares"; "slotTabs" may resolve to null
        activity.binding.slotTabs = null;
        android.widget.LinearLayout strip = new android.widget.LinearLayout(activity);
        strip.setTag("tab-squares");
        for (String title : new String[]{"Docs", "Mail"}) {
            android.widget.TextView chip = new android.widget.TextView(activity);
            chip.setText(title);
            chip.setTag("tab");
            strip.addView(chip);
        }
        ViewGroup content = activity.findViewById(android.R.id.content);
        ((ViewGroup) content.getChildAt(0)).addView(strip);

        SlothShell.own(activity);
        View drawer = drawerOf(activity);

        assertNotNull("first tab mirrored", findChip(drawer, "Docs"));
        assertNotNull("second tab mirrored", findChip(drawer, "Mail"));
    }

    @Test
    public void theBarAndTheDrawerAreOneClippedSurface() {
        TestActivity activity = build();
        SlothShell.own(activity);
        View surface = findByViewTag(overlayOf(activity), "surface");
        assertNotNull(surface);
        assertTrue("the surface clips, so it cannot spill", ((ViewGroup) surface).getClipChildren());
        assertTrue("and clips to its own outline", surface.getClipToOutline());

        View column = findByViewTag(surface, "column");
        assertNotNull("the bar and the drawer share one column", column);
        // bar, then drawer: the hairline between them is gone, because a rule between two
        // halves is the cheapest way to draw them as two
        assertEquals("bar and drawer, nothing between", 2, ((ViewGroup) column).getChildCount());
        assertTrue("the drawer is a child of the panel",
                drawerOf(activity).getParent() == column);
        assertTrue("the bar sits inside the panel's drag wrapper",
                findByViewTag(column, "bar").getParent() == ((ViewGroup) column).getChildAt(0));
    }

    @Test
    public void aSidewaysSwipeOnTheBarIsNotADrag() {
        TestActivity activity = build();
        SlothShell.own(activity);
        View bar = findByViewTag(overlayOf(activity), "drag");

        long t = SystemClock.uptimeMillis();
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 300f, 40f, 0));
        // 120dp sideways, 2dp down: the address swipe, not a drawer drag
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t + 30, MotionEvent.ACTION_MOVE, 300f - px(120), 42f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, 300f - px(120), 42f, 0));
        settle();

        assertEquals("a sideways swipe must not grow the bar", 0,
                drawerOf(activity).getLayoutParams().height);
    }

    @Test
    public void aTinyWobbleIsNotADragEither() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        View bar = findByViewTag(overlayOf(activity), "drag");

        long t = SystemClock.uptimeMillis();
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 40f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_MOVE, 201f, 46f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, 201f, 46f, 0));
        settle();

        // a tap shows the strip first: the peek is the drawer's front door now
        assertEquals("a wobble under the slop is a tap, and a tap opens the peek",
                peekHeight(activity),
                ((ViewGroup.LayoutParams) drawerOf(activity).getLayoutParams()).height);
        assertEquals("with the tab strip showing", View.VISIBLE,
                findByViewTag(drawerOf(activity), "peek-strip").getVisibility());
    }

    @Test
    public void theDrawerSitsBelowTheBarNotUnderIt() {
        TestActivity activity = build();
        SlothShell.own(activity);
        View bar = findByViewTag(overlayOf(activity), "drag");
        View surface = findByViewTag(overlayOf(activity), "surface");
        ((ViewGroup) surface).measure(
                android.view.View.MeasureSpec.makeMeasureSpec(px(400), android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(px(800), android.view.View.MeasureSpec.EXACTLY));
        ((ViewGroup) surface).layout(0, 0, px(400), surface.getMeasuredHeight());

        // open it by dragging the bar down
        long t = SystemClock.uptimeMillis();
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 40f, 0));
        driveHeightSafe2(activity, px(300));
        settle();
        ((ViewGroup) surface).measure(
                android.view.View.MeasureSpec.makeMeasureSpec(px(400), android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(px(800), android.view.View.MeasureSpec.EXACTLY));
        ((ViewGroup) surface).layout(0, 0, px(400), surface.getMeasuredHeight());

        View drawer = drawerOf(activity);
        System.out.println("GEOMETRY bar=" + bar.getHeight() + " top=" + bar.getTop()
                + " bottom=" + bar.getBottom() + " drawerTop=" + drawer.getTop()
                + " drawerH=" + drawer.getHeight() + " surfacePad=" + surface.getPaddingTop());
        assertTrue("the drawer starts where the bar ends: drawer " + drawer.getTop()
                        + " bar bottom " + bar.getBottom(),
                drawer.getTop() >= bar.getBottom());
    }

    @Test
    public void editModeHidesAWidgetAndRemembersIt() {
        TestActivity activity = build();
        SlothShell.own(activity);
        View drawer = drawerOf(activity);

        tapChip(drawer, "Edit widgets");
        settle();
        tapChip(findByViewTag(drawerOf(activity), "widgets"), "Calculator");
        settle();

        TextView calcLabel = (TextView) findChip(drawer, "CALC");
        assertTrue("the hidden widget is gone once editing ends",
                calcLabel == null || !isShown(calcLabel));
        assertNotNull("the clock stays", findChip(drawer, "CLOCK"));

        // and the choice survives a relaunch
        TestActivity again = build();
        SlothShell.own(again);
        TextView afterRelaunch = (TextView) findChip(drawerOf(again), "CALC");
        assertTrue("the choice is remembered", afterRelaunch == null || !isShown(afterRelaunch));

        // and the chooser offers a way back
        tapChip(drawerOf(again), "Edit widgets");
        settle();
        assertNotNull(findTagged(findByViewTag(drawerOf(again), "widgets"), "Calculator"));
    }

    @Test
    public void theClockIsNotAdvertisedAsClickable() {
        TestActivity activity = build();
        SlothShell.own(activity);

        TextView clock = (TextView) findChip(drawerOf(activity), "CLOCK");
        assertNotNull(clock);
        View tile = clock;
        while (tile != null && String.valueOf(tile.getContentDescription()).isEmpty()) {
            tile = tile.getParent() instanceof View ? (View) tile.getParent() : null;
        }
        assertNotNull(tile);
        assertFalse("a card that does nothing must not claim to be clickable", tile.isClickable());
    }

    @Test
    public void theClockLoopStopsWhenTheShellIsReplaced() {
        final int[] ticks = new int[1];
        SlothShell.every(1000, new Runnable() {
            @Override
            public void run() {
                ticks[0]++;
            }
        });
        org.robolectric.shadows.ShadowLooper.idleMainLooper(3500,
                java.util.concurrent.TimeUnit.MILLISECONDS);
        int before = ticks[0];
        assertTrue("the loop was running: " + before, before > 0);

        TestActivity fresh = build();
        SlothShell.own(fresh); // replaces the shell, which must end the old loop

        org.robolectric.shadows.ShadowLooper.idleMainLooper(3500,
                java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals("a loop from the previous shell kept running", before, ticks[0]);
    }

    @Test
    public void theAppsOwnChromeIsCollapsedEvenWhileSettingsBorrowsThePanel() {
        TestActivity activity = build();
        SlothShell.own(activity);
        openBrowserPanel(activity);
        assertEquals("settings does not borrow the app's panel any more", View.GONE,
                activity.binding.panel.getVisibility());

        SlothShell.own(activity); // the app repaints while settings is open

        assertEquals("the app's bar must not come back as an empty strip", View.GONE,
                activity.binding.chromeDock.getVisibility());
        assertEquals(View.GONE, activity.binding.chromeToolRow.getVisibility());
    }

    @Test
    public void thePadlockIsTheSiteControl() {
        TestActivity activity = build();
        SlothShell.own(activity);
        findAddress(overlayOf(activity)).setText("https://sloth.example/leaf");
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        View lock = findLock(overlayOf(activity));
        assertNotNull("the padlock sits in the address field", lock);
        assertTrue("and it opens site settings", lock.isClickable());
    }

    @Test
    public void theBarRecedesAsTheDashboardArrives() {
        TestActivity activity = build();
        SlothShell.own(activity);
        View bar = findByViewTag(overlayOf(activity), "drag");
        View plus = findTagged(bar, "New tab");

        float collapsed = plus == null ? 1f : plus.getAlpha();

        long t = SystemClock.uptimeMillis();
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 40f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t + 15, MotionEvent.ACTION_MOVE, 200f, 340f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t + 25, MotionEvent.ACTION_UP, 200f, 500f, 0));
        settle();

        assertEquals("the drawer is open", true,
                drawerOf(activity).getLayoutParams().height > px(150));
        // the bar is the dashboard's header now: it does not step back, because stepping
        // back said "something else is in front of me"
        float kept = findTagged(bar, "New tab").getAlpha();
        assertEquals("the bar stays at full weight while the dashboard is up",
                collapsed, kept, 0.01f);
    }

    @Test
    public void closingRestoresTheBarCompletely() {
        TestActivity activity = build();
        SlothShell.own(activity);
        View bar = findByViewTag(overlayOf(activity), "drag");

        long t = SystemClock.uptimeMillis();
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 40f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t + 15, MotionEvent.ACTION_MOVE, 200f, 340f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t + 25, MotionEvent.ACTION_UP, 200f, 500f, 0));
        settle();
        long t2 = SystemClock.uptimeMillis();
        bar.dispatchTouchEvent(MotionEvent.obtain(t2, t2, MotionEvent.ACTION_DOWN, 200f, 400f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(t2, t2 + 15, MotionEvent.ACTION_MOVE, 200f, 200f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(t2, t2 + 25, MotionEvent.ACTION_UP, 200f, 60f, 0));
        settle();

        assertEquals("closed", 0, drawerOf(activity).getLayoutParams().height);
        assertEquals("the bar is fully back", 1f, findTagged(bar, "New tab").getAlpha(), 0.02f);
    }

    @Test
    public void aHalfOpenDrawerDoesNotShowItsFooterHangingInSpace() {
        TestActivity activity = build();
        SlothShell.own(activity);
        View bar = findByViewTag(overlayOf(activity), "drag");

        // a short drag: barely past the peek, then released slowly
        long t = SystemClock.uptimeMillis();
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 40f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t + 30, MotionEvent.ACTION_MOVE, 200f, 120f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, 200f, 120f, 0));
        settle();

        View foot = findChip(drawerOf(activity), "Settings");
        assertTrue("the footer is either absent or not laid out mid-drag",
                foot == null || !isShown(foot));

        long t2 = SystemClock.uptimeMillis();
        bar.dispatchTouchEvent(MotionEvent.obtain(t2, t2, MotionEvent.ACTION_DOWN, 200f, 40f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(t2, t2 + 15, MotionEvent.ACTION_MOVE, 200f, 400f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(t2, t2 + 25, MotionEvent.ACTION_UP, 200f, 700f, 0));
        settle();
        assertNotNull("and it is there once the drawer is open", findChip(drawerOf(activity), "Settings"));
    }

    @Test
    public void closingIsAnimatedRatherThanACut() {
        TestActivity activity = build();
        SlothShell.own(activity);
        View bar = findByViewTag(overlayOf(activity), "drag");

        long t = SystemClock.uptimeMillis();
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 40f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t + 15, MotionEvent.ACTION_MOVE, 200f, 400f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t + 25, MotionEvent.ACTION_UP, 200f, 700f, 0));
        settle();
        int open = drawerOf(activity).getLayoutParams().height;
        assertTrue("open first", open > px(200));

        long t2 = SystemClock.uptimeMillis();
        // a short upward flick: it leaves the drawer open, but on its way out
        bar.dispatchTouchEvent(MotionEvent.obtain(t2, t2, MotionEvent.ACTION_DOWN, 200f, 400f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(t2, t2 + 15, MotionEvent.ACTION_MOVE, 200f, 340f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(t2, t2 + 25, MotionEvent.ACTION_UP, 200f, 300f, 0));

        // one frame in, it is on its way down - not already gone
        org.robolectric.shadows.ShadowLooper.idleMainLooper(32,
                java.util.concurrent.TimeUnit.MILLISECONDS);
        int midway = drawerOf(activity).getLayoutParams().height;
        assertTrue("closing travels: " + midway + " of " + open, midway > 0 && midway < open);

        settle();
        int landed = drawerOf(activity).getLayoutParams().height;
        assertEquals("a flick up from a tall drawer shuts it", 0, landed);
    }

    @Test
    public void theDrawerHeaderCanBeDraggedToo() {
        TestActivity activity = build();
        SlothShell.own(activity);
        View bar = findByViewTag(overlayOf(activity), "drag");
        View drawer = drawerOf(activity);

        long t = SystemClock.uptimeMillis();
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 40f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t + 15, MotionEvent.ACTION_MOVE, 200f, 500f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t + 25, MotionEvent.ACTION_UP, 200f, 700f, 0));
        settle();
        int open = drawer.getLayoutParams().height;

        assertNull("there is no second header to drag", findByViewTag(drawer, "head-drag"));

        // the content above its own top is still a drag of the sheet: the list hands an
        // upward pull to the drawer, which is how a finger on the dashboard closes it
        View list = findByViewTag(drawer, "list");
        assertNotNull("the dashboard has a scroller", list);
        long t2 = SystemClock.uptimeMillis();
        list.dispatchTouchEvent(MotionEvent.obtain(t2, t2, MotionEvent.ACTION_DOWN, 200f, 100f, 0));
        list.dispatchTouchEvent(MotionEvent.obtain(t2, t2 + 30, MotionEvent.ACTION_MOVE, 200f, 20f, 0));
        list.dispatchTouchEvent(MotionEvent.obtain(t2, t2 + 60, MotionEvent.ACTION_MOVE, 200f, -60f, 0));
        list.dispatchTouchEvent(MotionEvent.obtain(t2, t2 + 90, MotionEvent.ACTION_UP, 200f, -140f, 0));
        settle();

        assertNotEquals("the dashboard's own content drags the sheet like the bar does",
                open, drawer.getLayoutParams().height);
    }

    @Test
    public void theThemeChipChangesTheWholePalette() {
        TestActivity activity = build();
        SlothShell.own(activity);
        assertEquals("starts on Moss", 0xFFC9F27E, SlothShell.ACCENT);

        findByViewTag(drawerOf(activity), "palette-chip").performClick();
        settle();
        View picker = findByViewTag(drawerOf(activity), "palette");
        assertNotNull("the chip opens the palette list", picker);
        assertEquals("it is on show", View.VISIBLE, picker.getVisibility());

        tapChip(picker, "Moonlit Forest");
        settle();

        assertEquals("Moonlit is cyan", 0xFF4FE0D2, SlothShell.ACCENT);
        assertEquals("and its ink is cooler", 0xFFE6F4F7, SlothShell.INK);
    }

    @Test
    public void theChosenThemeSurvivesARelaunch() {
        TestActivity activity = build();
        SlothShell.own(activity);
        findByViewTag(drawerOf(activity), "palette-chip").performClick();
        settle();
        tapChip(findByViewTag(drawerOf(activity), "palette"), "Moonlit Forest");
        settle();

        TestActivity again = build();
        SlothShell.own(again);
        assertEquals("the palette is remembered", 0xFF4FE0D2, SlothShell.ACCENT);
        assertNotNull("and the chip says which one", findChip(drawerOf(again), "Moonlit"));
        int rows = countByTag(drawerOf(again), "palette-row");
        assertEquals("every palette is offered", SlothTheme.NAMES.length, rows);
    }

    @Test
    public void everyPaletteIsReadableNotJustPretty() {
        TestActivity activity = build();
        for (int i = 0; i < SlothTheme.NAMES.length; i++) {
            SlothTheme.select(activity, i);
            double ratio = contrast(SlothTheme.ink(), SlothTheme.bg());
            assertTrue(SlothTheme.NAMES[i] + " text on background is only " + String.format("%.1f", ratio) + ":1",
                    ratio >= 4.5);
            double dim = contrast(SlothTheme.muted(), SlothTheme.card());
            assertTrue(SlothTheme.NAMES[i] + " secondary text on cards is only "
                    + String.format("%.1f", dim) + ":1", dim >= 4.5);
            double onCard = contrast(SlothTheme.ink(), SlothTheme.card());
            assertTrue(SlothTheme.NAMES[i] + " text on cards is only " + String.format("%.1f", onCard) + ":1",
                    onCard >= 4.5);
            assertNotEquals(SlothTheme.NAMES[i] + " accent must differ from the background",
                    SlothTheme.accent(), SlothTheme.bg());
        }
        SlothTheme.select(activity, 0);
    }

    private double contrast(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        double hi = Math.max(la, lb);
        double lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }

    private double luminance(int colour) {
        double[] c = new double[3];
        int[] shift = {16, 8, 0};
        for (int i = 0; i < 3; i++) {
            double v = ((colour >> shift[i]) & 0xFF) / 255.0;
            c[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    @Test
    public void theCountersShowNumbersThatExist() {
        TestActivity activity = build();
        for (String title : new String[]{"Docs", "Mail"}) {
            android.widget.TextView chip = new android.widget.TextView(activity);
            chip.setText(title);
            chip.setTag("tab");
            activity.binding.slotTabs.addView(chip);
        }
        SlothShell.own(activity);
        View drawer = drawerOf(activity);

        // the fixture's own counter says 3, and the app's number wins over the strip
        assertNotNull("tabs counter", findChip(drawer, "3 tabs"));
        assertNotNull("note counter", findChip(drawer, "empty"));
    }

    @Test
    public void eachPaletteHasItsOwnCornerShape() {
        TestActivity activity = build();
        SlothTheme.select(activity, 9); // Ancient Bark: cut close
        float bark = SlothTheme.radius();
        SlothTheme.select(activity, 4); // Grotto: soft
        float grotto = SlothTheme.radius();
        assertTrue("the concepts differ in material, not only colour: " + bark + " vs " + grotto,
                bark < grotto);
        SlothTheme.select(activity, 0);
    }

    @Test
    public void tabCardsLookLikeThumbnailsNotRows() {
        TestActivity activity = build();
        android.widget.TextView chip = new android.widget.TextView(activity);
        chip.setText("Docs");
        chip.setTag("tab");
        activity.binding.slotTabs.addView(chip);
        SlothShell.own(activity);
        View drawer = drawerOf(activity);

        View label = findChip(drawer, "Docs");
        assertNotNull(label);
        View card = (View) label.getParent().getParent(); // title row, then the card
        assertEquals("a card carries the page's initial", "D", firstInitial(card));
        assertNotNull("and its own close control", findTagged(drawer, "Close Docs"));
        assertTrue("and is a tall card, not a thin row", card.getLayoutParams().height >= 100);
    }

    private String firstInitial(View card) {
        final String[] out = {""};
        walk(card, new ViewVisitor() {
            @Override
            public void visit(View v) {
                if (out[0].isEmpty() && v instanceof android.widget.TextView) {
                    String t = ((android.widget.TextView) v).getText().toString();
                    if (t.length() == 1) {
                        out[0] = t;
                    }
                }
            }
        });
        return out[0];
    }

    @Test
    public void cardsAreGlassAndItFollowsThePalette() {
        TestActivity activity = build();

        SlothTheme.select(activity, 1); // Moonlit: dark glass
        assertTrue("dark palettes are frosted", SlothTheme.frosted());
        assertTrue("a dark card lifts towards the light at its top edge",
                luminance(SlothTheme.cardTop()) > luminance(SlothTheme.card()));

        SlothTheme.select(activity, 3); // Alabaster: paper
        assertFalse("light palettes are paper, not glass", SlothTheme.frosted());
        assertTrue("a light card sits a shade below its top edge",
                luminance(SlothTheme.cardTop()) < luminance(SlothTheme.card()));

        SlothTheme.select(activity, 0);
    }

    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void theDrawerHasAPaintedForestBehindIt() {
        TestActivity activity = build();
        SlothShell.own(activity);
        View backdrop = findByViewTag(overlayOf(activity), "backdrop");
        assertNotNull("the panel has a scene behind it", backdrop);

        backdrop.measure(View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY));
        backdrop.layout(0, 0, 900, 700);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(900, 700,
                android.graphics.Bitmap.Config.ARGB_8888);
        backdrop.draw(new android.graphics.Canvas(bitmap));

        java.util.Set<Integer> colours = new java.util.HashSet<>();
        for (int x = 0; x < 900; x += 15) {
            for (int y = 0; y < 700; y += 15) {
                colours.add(bitmap.getPixel(x, y));
            }
        }
        assertTrue("the backdrop actually draws something: " + colours.size(), colours.size() > 8);
        assertNotEquals("and it is not one flat wash", 1, colours.size());
    }

    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void thereIsASlothInTheScene() {
        TestActivity activity = build();
        SlothShell.own(activity);
        View backdrop = findByViewTag(overlayOf(activity), "backdrop");
        assertNotNull(backdrop);

        backdrop.measure(View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY));
        backdrop.layout(0, 0, 900, 700);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(900, 700,
                android.graphics.Bitmap.Config.ARGB_8888);
        backdrop.draw(new android.graphics.Canvas(bitmap));

        // his body is drawn in a colour only he uses, so finding it proves he is there
        int body = com.sloth.browser.SlothBackdrop.bodyColour();
        int found = 0;
        float cx = com.sloth.browser.SlothBackdrop.slothX(900);
        float cy = com.sloth.browser.SlothBackdrop.slothY(700);
        float size = com.sloth.browser.SlothBackdrop.slothSize(900);
        for (int x = (int) (cx - size); x < (int) (cx + size); x++) {
            for (int y = (int) (cy - size); y < (int) (cy + size); y++) {
                if (x >= 0 && y >= 0 && x < 900 && y < 700 && bitmap.getPixel(x, y) == body) {
                    found++;
                }
            }
        }
        assertTrue("the sloth himself is missing from the scene: " + found + " pixels", found > 500);
    }

    /* -------------------------------------------------------------------
     * Renders every state of the shell to a PNG so the layout can be looked at
     * rather than argued about. A three-button navigation bar is drawn over the
     * bottom of each shot, because that is what sits there on the phone.
     * ------------------------------------------------------------------- */

    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @org.robolectric.annotation.Config(qualifiers = "w411dp-h891dp-xxhdpi")
    public void renderEveryStateForReview() throws Exception {
        // a visual pass, not a gate: it builds ten windows and draws each one, which is minutes
        // of work and proves nothing a test can assert. Opt in with SLOTH_RENDER=1 so the suite
        // stays a suite.
        org.junit.Assume.assumeTrue("set SLOTH_RENDER=1 to run the render pass",
                "1".equals(System.getenv("SLOTH_RENDER")));
        java.io.File dir = new java.io.File("/opt/data/cache/scratch/shots");
        dir.mkdirs();
        SlothShell.testNavInset = 144; // a three-button navigation bar
        TestActivity activity = build();
        addTabs(activity, "Docs", "Mail", "News");
        SlothShell.own(activity);
        settle();
        shoot(activity, new java.io.File(dir, "01-collapsed.png"));

        View handle = findByViewTag(overlayOf(activity), "drag");
        long t0 = android.os.SystemClock.uptimeMillis();
        handle.dispatchTouchEvent(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, 200f, 400f, 0));
        handle.dispatchTouchEvent(MotionEvent.obtain(t0, t0 + 40, MotionEvent.ACTION_UP, 200f, 400f, 0));
        settle();
        shoot(activity, new java.io.File(dir, "02-peek.png"));
        driveHeight(activity, peekHeight(activity));
        settle();
        shoot(activity, new java.io.File(dir, "02b-peek-strip.png"));

        driveHeight(activity, 1200);
        settle();
        shoot(activity, new java.io.File(dir, "03-open.png"));

        driveHeight(activity, 2400);
        settle();
        shoot(activity, new java.io.File(dir, "04-full.png"));

        SlothShell.openDiagPage(activity);
        settle();
        shoot(activity, new java.io.File(dir, "17-diagnostics.png"));
        findByViewTag(overlayOf(activity), "diag-page").setVisibility(View.GONE);

        SlothShell.openLibrary(activity, "history");
        settle();
        shoot(activity, new java.io.File(dir, "18-library.png"));
        SlothShell.backOneLayer(activity);
        settle();

        if (findByViewTag(drawerOf(activity), "palette-chip") != null) {
            findByViewTag(drawerOf(activity), "palette-chip").performClick();
            settle();
            shoot(activity, new java.io.File(dir, "06-palette.png"));
            findByViewTag(drawerOf(activity), "palette").setVisibility(View.GONE);
        }

        driveHeight(activity, 0);
        settle();
        shoot(activity, new java.io.File(dir, "05-closed.png"));
        SlothLog.clear();
        SlothCover.probe(activity, "render closed");
        System.out.println("[probe] " + SlothLog.text().replace('\n', '|'));

        tapChip(drawerOf(activity), "Settings");
        settle();
        shoot(activity, new java.io.File(dir, "11-settings.png"));
        View page = findByViewTag(overlayOf(activity), "settings-page");
        View scroller = scrollerIn(page);
        if (scroller != null) {
            scroller.measure(View.MeasureSpec.makeMeasureSpec(1233, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(2673, View.MeasureSpec.EXACTLY));
            scroller.layout(0, 0, 1233, 2673);
            ((android.widget.ScrollView) scroller).fullScroll(View.FOCUS_DOWN);
            settle();
            shoot(activity, new java.io.File(dir, "15-settings-bottom.png"));
        }
        tapByDescription(overlayOf(activity), "Back to the drawer");
        settle();

        // the whole drawer in the three palettes that stress the most - light, mid, dark
        int[] light = {3, 0, 9};
        String[] wide = {"12-alabaster-open", "13-moss-open", "14-bark-open"};
        for (int i = 0; i < light.length; i++) {
            SlothTheme.select(activity, light[i]);
            TestActivity fresh = build();
            SlothShell.own(fresh);
            settle();
            driveHeight(fresh, 1500);
            settle();
            shoot(fresh, new java.io.File(dir, wide[i] + ".png"));
        }
        SlothTheme.select(activity, 0);
        TestActivity back = build();
        SlothShell.own(back);

        // the same drawer in each material, so the concepts can be compared side by side
        String[] names = {"07-alabaster", "08-bark", "09-twilight", "10-grotto"};
        int[] which = {3, 9, 6, 4};
        for (int i = 0; i < which.length; i++) {
            SlothTheme.select(activity, which[i]);
            TestActivity themed = build();
            SlothShell.own(themed);
            settle();
            driveHeight(themed, 1500);
            settle();
            shoot(themed, new java.io.File(dir, names[i] + ".png"));
        }
        SlothTheme.select(activity, 0);
        SlothShell.testNavInset = -1;
    }

    private void shoot(Activity activity, java.io.File out) throws Exception {
        View root = activity.findViewById(android.R.id.content);
        int w = 1233;
        int h = 2673;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(w, h,
                android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        root.draw(canvas);

        // the system navigation bar, as the phone draws it over everything
        android.graphics.Paint nav = new android.graphics.Paint();
        nav.setColor(0xE6101010);
        canvas.drawRect(0, h - 144, w, h, nav);
        nav.setColor(0xFFBFBFBF);
        float cy = h - 72;
        canvas.drawCircle(w * 0.16f, cy, 14, nav);
        android.graphics.Rect r = new android.graphics.Rect((int) (w * 0.49f), (int) (cy - 14),
                (int) (w * 0.51f), (int) (cy + 14));
        canvas.drawRect(r, nav);
        android.graphics.Path tri = new android.graphics.Path();
        tri.moveTo(w * 0.83f, cy - 16);
        tri.lineTo(w * 0.86f, cy);
        tri.lineTo(w * 0.80f, cy);
        tri.close();
        canvas.drawPath(tri, nav);

        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos);
        }
    }

    /** A drag reports the travel since the claim, never just the newest sample. */
    @Test
    public void aStripDragReportsTotalTravelNotTheLastStep() throws Exception {
        TestActivity activity = build();
        final float[] reported = new float[1];
        final boolean[] started = new boolean[1];
        SlothDrag drag = new SlothDrag(new SlothBar.Listener() {
            @Override public void onDragStart(float y, float carry) { started[0] = true; }
            @Override public void onDrag(float dy) { reported[0] = dy; }
            @Override public void onRelease(float velocity) { }
            @Override public boolean onSwipe(float dx) { return false; }
            @Override public void onTap() { }
        });
        View probe = new View(activity);
        long t = SystemClock.uptimeMillis();
        drag.onTouch(probe, MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 100f, 400f, 0));
        for (int i = 1; i <= 3; i++) {
            drag.onTouch(probe, MotionEvent.obtain(t, t + i * 16L,
                    MotionEvent.ACTION_MOVE, 100f, 400f - i * 50f, 0));
        }
        assertTrue("the drag was claimed", started[0]);
        // the move that claims the gesture is the claim, and the strip does not carry that
        // travel; the two moves after it must still add up, which is what used to fail
        assertEquals("the travel after the claim is carried whole",
                -100f, reported[0], 0.5f);
    }

    private void drag(View target, float fromY, float toY) {
        long t = android.os.SystemClock.uptimeMillis();
        target.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, fromY, 0));
        for (int i = 1; i <= 6; i++) {
            float y = fromY + (toY - fromY) * i / 6f;
            target.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 16L,
                    MotionEvent.ACTION_MOVE, 200f, y, 0));
        }
        target.dispatchTouchEvent(MotionEvent.obtain(t, t + 110L, MotionEvent.ACTION_UP, 200f, toY, 0));
    }

    /** The measured peek seat, read the way the shell holds it. */
    private int peekRowCount(Activity activity) {
        View strip = findByViewTag(drawerOf(activity), "peek-strip");
        if (!(strip instanceof ViewGroup)) {
            return -1;
        }
        return ((ViewGroup) strip).getChildCount();
    }

    private int peekHeight(Activity activity) throws Exception {
        java.lang.reflect.Field f = SlothShell.class.getDeclaredField("peekPx");
        f.setAccessible(true);
        int peek = (Integer) f.get(null);
        assertTrue("the shell has measured a peek seat: " + peek, peek > 0);
        return peek;
    }

    /** -1 means the full-height state the shell caps at. */
    private void driveHeight(Activity activity, int height) throws Exception {
        java.lang.reflect.Method m = SlothShell.class.getDeclaredMethod("setDrawerHeight",
                Activity.class, int.class);
        m.setAccessible(true);
        if (height < 0) {
            java.lang.reflect.Method full = SlothShell.class.getDeclaredMethod("openHeight",
                    Activity.class);
            full.setAccessible(true);
            height = (Integer) full.invoke(null, activity);
        }
        m.invoke(null, activity, height);
    }

    private void addTabs(Activity activity, String... titles) {
        FakeBinding b = ((TestActivity) activity).binding;
        for (String title : titles) {
            android.widget.TextView chip = new android.widget.TextView(activity);
            chip.setText(title);
            chip.setTag("tab");
            // a real browser always has one tab you are looking at
            chip.setSelected(b.slotTabs.getChildCount() == 0);
            b.slotTabs.addView(chip);
        }
    }

    @Test
    public void theDrawerKeepsItsButtonsAboveTheNavigationBar() {
        SlothShell.testNavInset = 144;
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        View drawer = drawerOf(activity);
        assertEquals("the sheet leaves the system bar its own strip",
                144, drawer.getPaddingBottom());

        driveHeightSafe(activity);
        assertEquals("and keeps it when the drawer is at its tallest",
                144, drawerOf(activity).getPaddingBottom());
        SlothShell.testNavInset = -1;
    }

    private void driveHeightSafe(Activity activity) {
        try {
            java.lang.reflect.Method m = SlothShell.class.getDeclaredMethod("setDrawerHeight",
                    Activity.class, int.class);
            m.setAccessible(true);
            m.invoke(null, activity, 2400);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void thePeekEndsOnAWholeRowOfTabs() {
        TestActivity activity = build();
        addTabs(activity, "Docs", "Mail", "News");
        SlothShell.own(activity);
        settle();

        View handle = findByViewTag(overlayOf(activity), "drag");
        long t = SystemClock.uptimeMillis();
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 400f, 0));
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, 200f, 400f, 0));
        settle();

        View drawer = drawerOf(activity);
        View card = findChip(drawer, "Docs");
        assertNotNull(card);
        int[] cardPos = new int[2];
        int[] drawerPos = new int[2];
        card.getLocationOnScreen(cardPos);
        drawer.getLocationOnScreen(drawerPos);
        int cardBottom = cardPos[1] + card.getHeight();
        int drawerBottom = drawerPos[1] + drawer.getHeight();
        assertTrue("the smallest open drawer cuts through a tab card: card ends at "
                        + cardBottom + ", drawer at " + drawerBottom,
                drawerBottom >= cardBottom);
    }

    @Test
    public void theStatusBarTakesTheDrawersColour() {
        TestActivity activity = build();
        SlothTheme.select(activity, 3); // Alabaster, a light palette
        SlothShell.own(activity);
        settle();
        // the colour is a view of ours now: the window itself is transparent under it, which
        // is the only thing that works on Android 15 and the only thing that cannot mismatch
        View scrim = findByViewTag(overlayOf(activity), "status-scrim");
        assertNotNull("a strip to paint", scrim);
        assertEquals("the strip matches the surface", SlothTheme.surface(),
                ((android.graphics.drawable.ColorDrawable) scrim.getBackground()).getColor());
        assertEquals("and the window is left clear", android.graphics.Color.TRANSPARENT,
                activity.getWindow().getStatusBarColor());
        SlothTheme.select(activity, 0);
    }

    @Test
    public void aDrawerCaughtMidDragFadesItsCutEdge() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        View drawer = drawerOf(activity);
        assertNull("there is no fade band: the panel is one surface, not a cut one",
                findByViewTag(drawer, "fade"));

        driveHeightSafe2(activity, 900); // between resting sizes
        assertNull("and still none mid-drag",
                findByViewTag(drawerOf(activity), "fade"));
    }

    private void driveHeightSafe2(Activity activity, int height) {
        try {
            java.lang.reflect.Method m = SlothShell.class.getDeclaredMethod("setDrawerHeight",
                    Activity.class, int.class);
            m.setAccessible(true);
            m.invoke(null, activity, height);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void thePaletteListSitsAboveTheDrawerAndClosesIt() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        View drawer = drawerOf(activity);
        findByViewTag(drawer, "palette-chip").performClick();
        settle();

        View picker = findByViewTag(drawer, "palette");
        assertNotNull(picker);
        assertEquals("the list is showing", View.VISIBLE, picker.getVisibility());
        ViewGroup sheet = (ViewGroup) picker.getParent();
        assertEquals("and it is the topmost thing in the drawer, not under it",
                sheet.getChildCount() - 1, sheet.indexOfChild(picker));

        // choosing a palette should put the drawer away, not leave it standing open
        tapChip(picker, "Ancient Bark");
        settle();
        int height = 0;
        try {
            java.lang.reflect.Field f = SlothShell.class.getDeclaredField("drawer");
            f.setAccessible(true);
            View now = (View) f.get(null);
            height = now.getLayoutParams().height;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertEquals("choosing a palette closes the drawer", 0, height);
        SlothTheme.select(activity, 0);
    }

    @Test
    public void theDrawerFollowsTheFingerInsteadOfJumpingToItsFittedSize() {
        TestActivity activity = build();
        addTabs(activity, "Docs", "Mail");
        SlothShell.own(activity);
        settle();
        View bar = findByViewTag(overlayOf(activity), "drag");

        drift(bar, 200f, 800f);
        int height = drawerOf(activity).getLayoutParams().height;
        assertTrue("a 600px drag should leave the drawer roughly 600px tall, not snap: " + height,
                height > 400);

        int before = height;
        drift(bar, 800f, 500f);
        int after = drawerOf(activity).getLayoutParams().height;
        assertTrue("and dragging back up should shorten it: " + before + " -> " + after,
                after < before);
    }

    @Test
    public void aFlickDownGoesAllTheWayInsteadOfStoppingAtTheFittedSize() {
        TestActivity activity = build();
        addTabs(activity, "Docs", "Mail");
        SlothShell.own(activity);
        settle();
        View bar = findByViewTag(overlayOf(activity), "drag");

        long t = SystemClock.uptimeMillis();
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 300f, 0));
        for (int i = 1; i <= 4; i++) {
            bar.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 12L,
                    MotionEvent.ACTION_MOVE, 200f, 300f + i * 120f, 0));
        }
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t + 50L, MotionEvent.ACTION_UP, 200f, 780f, 0));
        settle();

        int height = drawerOf(activity).getLayoutParams().height;
        int screen = activity.getResources().getDisplayMetrics().heightPixels;
        assertTrue("a flick down should end well open, not mid-way: " + height + " of " + screen,
                height > screen * 0.45f);
    }

    /** Slow, deliberate drag: no flick, so the height should track the finger. */
    private void drift(View target, float fromY, float toY) {
        long t = SystemClock.uptimeMillis();
        target.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, fromY, 0));
        int steps = 10;
        for (int i = 1; i <= steps; i++) {
            float y = fromY + (toY - fromY) * i / steps;
            target.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 60L,
                    MotionEvent.ACTION_MOVE, 200f, y, 0));
        }
        target.dispatchTouchEvent(MotionEvent.obtain(t, t + steps * 60L + 200L,
                MotionEvent.ACTION_UP, 200f, toY, 0));
    }

    @Test
    public void anUntitledTabIsNotCalledTabsCommaOne() {
        TestActivity activity = build();
        // the browser labels an untitled tab by its spoken name, with no visible text
        android.widget.TextView untitled = new android.widget.TextView(activity);
        untitled.setContentDescription("Tabs, 1");
        untitled.setTag("tab");
        activity.binding.slotTabs.addView(untitled);
        SlothShell.own(activity);
        settle();
        View drawer = drawerOf(activity);

        assertNotNull("a card for the untitled tab", findChip(drawer, "Tab 1"));
        assertNull("and it is not labelled with the spoken form", findChip(drawer, "Tabs, 1"));
    }

    @Test
    public void theBarAndTheDrawerBecomeOnePanel() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        View bar = findByViewTag(overlayOf(activity), "bar");
        View surface = findByViewTag(overlayOf(activity), "surface");
        assertNull("the bar paints no fill of its own", bar.getBackground());
        android.graphics.drawable.Drawable fill = surface.getBackground();
        assertNotNull("the one fill is on the surface", fill);

        View handle = findByViewTag(overlayOf(activity), "drag");
        long t = SystemClock.uptimeMillis();
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 400f, 0));
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, 200f, 400f, 0));
        settle();

        assertTrue("and it is the same fill once the panel is open",
                fill == findByViewTag(overlayOf(activity), "surface").getBackground());
        assertNull("the bar still paints nothing",
                findByViewTag(overlayOf(activity), "bar").getBackground());
    }

    @Test
    public void anUpwardDragOnTheDrawerContentClosesIt() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();

        View handle = findByViewTag(overlayOf(activity), "drag");
        long t = SystemClock.uptimeMillis();
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 400f, 0));
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, 200f, 400f, 0));
        settle();
        driveHeightSafe2(activity, 640);
        settle();
        int open = drawerOf(activity).getLayoutParams().height;
        assertTrue("opened", open > 400);
        View list = findByViewTag(drawerOf(activity), "list");
        assertNotNull("the drawer's list", list);
        settle();
        long t2 = SystemClock.uptimeMillis();
        list.dispatchTouchEvent(MotionEvent.obtain(t2, t2, MotionEvent.ACTION_DOWN, 300f, 520f, 0));
        for (int i = 1; i <= 6; i++) {
            list.dispatchTouchEvent(MotionEvent.obtain(t2, t2 + i * 16L,
                    MotionEvent.ACTION_MOVE, 300f, 520f - i * 70f, 0));
        }
        list.dispatchTouchEvent(MotionEvent.obtain(t2, t2 + 110L, MotionEvent.ACTION_UP, 300f, 100f, 0));
        settle();

        assertTrue("dragging up on the content closes the drawer: "
                        + drawerOf(activity).getLayoutParams().height,
                drawerOf(activity).getLayoutParams().height < open);
    }

    @Test
    public void theGrabberClosesTheDrawerWithAFlick() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        driveHeightSafe2(activity, 520);
        settle();
        // the grabber band is gone with the unification: the strip is the handle now, and a
        // flick on it must still shut the sheet
        View strip = findByViewTag(drawerOf(activity), "peek-strip");
        assertNotNull("a strip to flick", strip);
        assertNull("and no invisible grabber band above the content",
                findByViewTag(drawerOf(activity), "grip"));

        // a real flick: the speed is measured over the last step, so the finger has to keep
        // moving into the lift-off, not stop dead before it
        long t = SystemClock.uptimeMillis();
        strip.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 300f, 40f, 0));
        for (int i = 1; i <= 6; i++) {
            strip.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 16L,
                    MotionEvent.ACTION_MOVE, 300f, 40f - i * 70f, 0));
        }
        strip.dispatchTouchEvent(MotionEvent.obtain(t, t + 6 * 16L + 8L,
                MotionEvent.ACTION_UP, 300f, -490f, 0));
        settle();


        assertEquals("a flick up on the grabber closes the drawer", 0,
                drawerOf(activity).getLayoutParams().height);
    }

    @Test
    public void theSystemBarInsetCanArriveLate() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        SlothShell.applyBottomInset(activity, 132);
        settle();
        assertEquals("the drawer keeps its footer above the system bar",
                132, drawerOf(activity).getPaddingBottom());
        SlothShell.applyBottomInset(activity, 0);
    }

    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void everyPaletteLooksDifferentNotJustTinted() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        View backdrop = findByViewTag(overlayOf(activity), "backdrop");
        java.util.Set<Integer> seen = new java.util.HashSet<>();

        for (int i = 0; i < SlothTheme.NAMES.length; i++) {
            SlothTheme.select(activity, i);
            backdrop.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY));
            backdrop.layout(0, 0, 600, 900);
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(600, 900,
                    android.graphics.Bitmap.Config.ARGB_8888);
            backdrop.draw(new android.graphics.Canvas(bitmap));
            int hash = 0;
            for (int x = 10; x < 600; x += 23) {
                for (int y = 10; y < 900; y += 23) {
                    hash = hash * 31 + bitmap.getPixel(x, y);
                }
            }
            assertTrue("palette " + SlothTheme.FULL[i] + " renders the same scene as another",
                    seen.add(hash));
        }
        assertEquals("one distinct scene per palette", SlothTheme.NAMES.length, seen.size());
        SlothTheme.select(activity, 0);
    }

    @Test
    public void thePickerUsesTheConceptNames() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        View drawer = drawerOf(activity);
        findByViewTag(drawer, "palette-chip").performClick();
        settle();
        View picker = findByViewTag(drawer, "palette");
        assertNotNull(findTagged(picker, "Ancient Bark"));
        assertNotNull(findTagged(picker, "Alabaster Grove"));
        assertNotNull(findTagged(picker, "Root-Level Moss"));
    }

    @Test
    public void addWidgetOpensAChooserThatActuallyRemovesWidgets() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        View drawer = drawerOf(activity);
        assertNotNull("the clock is on the drawer", findChip(drawer, "CLOCK"));

        tapChip(drawer, "Add widget");
        settle();
        View chooser = findByViewTag(drawer, "widgets");
        assertNotNull("the chooser", chooser);
        assertEquals("it is on show", View.VISIBLE, chooser.getVisibility());
        assertEquals("one row per widget", SlothBoard.KINDS.length, countByTag(chooser, "widget-row"));

        tapChip(chooser, "Clock");
        settle();

        View after = drawerOf(activity);
        View clock = findChip(after, "CLOCK");
        assertTrue("clock goes away when it is switched off",
                clock == null || !isShown(clock));
        assertNotNull("and the picker comes back", findByViewTag(after, "widgets"));
    }

    @Test
    public void settingsStepsOutFromUnderTheDrawer() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        View drawer = drawerOf(activity);
        tapChip(drawer, "Settings");
        settle();
        assertEquals("settings opens our own page first", View.VISIBLE,
                findByViewTag(overlayOf(activity), "settings-page").getVisibility());
        SlothShell.openAppSettings(activity, "");
        settle();

        assertEquals("and the drawer gets out of the way", 0,
                drawerOf(activity).getLayoutParams().height);
        View panel = activity.binding.panel;
        assertNotNull(panel);
        assertEquals("and the app's own panel stays shut - it was the empty box",
                View.GONE, panel.getVisibility());
        assertEquals("our page is the one on show", View.VISIBLE,
                findByViewTag(overlayOf(activity), "settings-page").getVisibility());
        // the app's own screen is no longer asked for: it draws into the panel, and that
        // panel is the empty box. Our page carries the switches instead.
        assertTrue("and it says where the request went: " + SlothLog.text(),
                SlothLog.text().contains("opened on our page"));
    }

    @Test
    public void settingsPageIsThemedAndItsRowsAreReal() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        tapChip(drawerOf(activity), "Settings");
        settle();

        View page = findByViewTag(overlayOf(activity), "settings-page");
        assertNotNull("the themed settings page", page);
        assertEquals(View.VISIBLE, page.getVisibility());
        assertNotNull("it shows the browser's own switches", findChip(page, "Block trackers"));
        assertTrue("its rows are the browser's own controls", countByTag(page, "settings-row") >= 16);

        tapChip(page, "Clear browsing data");
        settle();
        assertEquals("the row reached clearData, not a copy of it", 1, activity.cleared);

        tapChip(page, "Restart the engine");
        settle();
        assertEquals(1, activity.restarts);

        // a switch writes the browser's own field and asks it to save
        assertTrue(activity.settings.blockTrackers);
        tapChip(page, "Block trackers");
        settle();
        assertFalse("the switch reaches the browser's own model", activity.settings.blockTrackers);
        assertTrue("and it is saved", activity.settingsSaved > 0);
        View after = findByViewTag(overlayOf(activity), "settings-page");
        assertEquals("and the page stays up while the switch is flipped", View.VISIBLE,
                after.getVisibility());

        SlothShell.openSettingsPage(activity);
        settle();
        View again = findByViewTag(overlayOf(activity), "settings-page");
        tapChip(again, "Text zoom");
        settle();
        assertEquals("a number steps in place", 125, activity.settings.textZoom);

        tapByDescription(page, "Back to the drawer");
        settle();
        assertEquals("and back closes it", View.GONE,
                findByViewTag(overlayOf(activity), "settings-page").getVisibility());
    }

    /** Settings opens our page; the browser's own panel is one row further in. */
    private void openBrowserPanel(TestActivity activity) {
        // the app's own panel is opened directly now: the page no longer has a row for it,
        // because that row landed on a blank internal screen
        SlothShell.openAppSettings(activity, "");
        settle();
    }

    @Test
    public void openingTheDrawerOverTheSettingsPageBringsTheDrawerBack() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        SlothShell.openSettingsPage(activity);
        settle();
        View page = findByViewTag(overlayOf(activity), "settings-page");
        assertEquals(View.VISIBLE, page.getVisibility());

        View handle = findByViewTag(overlayOf(activity), "drag");
        long t = SystemClock.uptimeMillis();
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 500f, 0));
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_MOVE, 200f, 900f, 0));
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t + 120, MotionEvent.ACTION_UP, 200f, 900f, 0));
        settle();

        assertEquals("the page gets out of the drawer's way", View.GONE, page.getVisibility());
        assertTrue("and the drawer is open", drawerOf(activity).getLayoutParams().height > 0);
    }

    @Test
    public void aMissingMethodIsRecordedWhereThePersonCanReadIt() {
        TestActivity activity = build();
        assertFalse("a method that is not there", SlothSettings.call(activity, "definitelyNotAMethod"));
        assertTrue("is written down", SlothLog.text().contains("definitelyNotAMethod"));

        SlothShell.own(activity);
        settle();
        SlothShell.openSettingsPage(activity);
        settle();
        View page = findByViewTag(overlayOf(activity), "settings-page");
        assertNotNull("the diagnostics row is on the page", findChip(page, "Recent problems"));
        SlothLog.clear();
    }

    @Test
    public void switchingAWidgetLeavesTheDrawerOpenOnTheChooser() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        View handle = findByViewTag(overlayOf(activity), "drag");
        long t = SystemClock.uptimeMillis();
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 500f, 0));
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, 200f, 500f, 0));
        settle();

        tapChip(drawerOf(activity), "Add widget");
        settle();
        tapChip(findByViewTag(drawerOf(activity), "widgets"), "Calculator");
        settle();

        assertTrue("the drawer does not shut in your face",
                drawerOf(activity).getLayoutParams().height > 0);
        View chooser = findByViewTag(drawerOf(activity), "widgets");
        assertEquals("and the list is still there to keep going", View.VISIBLE,
                chooser.getVisibility());
    }

    @Test
    public void thePalettePickerOpensWhenItIsReachedFromSettings() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        SlothShell.openSettingsPage(activity);
        settle();

        tapChip(findByViewTag(overlayOf(activity), "settings-page"), "Palette");
        settle();

        View picker = findByViewTag(drawerOf(activity), "palette");
        assertNotNull("the palette list exists", picker);
        assertEquals("and it is on show", View.VISIBLE, picker.getVisibility());
        assertTrue("with a drawer to hold it",
                drawerOf(activity).getLayoutParams().height > 0);
    }

    @Test
    public void everyTransitionLeavesTheLayersAgreeing() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        assertNull("start", SlothShell.mislay());

        View handle = findByViewTag(overlayOf(activity), "drag");
        long t = SystemClock.uptimeMillis();
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 500f, 0));
        handle.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, 200f, 500f, 0));
        settle();
        assertNull("drawer open", SlothShell.mislay());

        tapChip(drawerOf(activity), "Add widget");
        settle();
        assertNull("widget list", SlothShell.mislay());

        tapChip(drawerOf(activity), "Settings");
        settle();
        assertNull("settings page", SlothShell.mislay());

        tapChip(findByViewTag(overlayOf(activity), "settings-page"), "Palette");
        settle();
        assertNull("palette from settings", SlothShell.mislay());

        tapChip(findByViewTag(drawerOf(activity), "palette"), "Alabaster Grove");
        settle();
        assertNull("after choosing a palette", SlothShell.mislay());
    }

    @Test
    public void closingTheDrawerTakesItsListsWithIt() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        SlothShell.openPalettePicker(activity);
        settle();
        View picker = findByViewTag(drawerOf(activity), "palette");
        assertEquals("the list is up", View.VISIBLE, picker.getVisibility());

        // shutting the drawer by hand, the way a person would
        View grip = findByViewTag(drawerOf(activity), "peek-strip");
        long t = SystemClock.uptimeMillis();
        grip.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 600f, 0));
        grip.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_MOVE, 200f, 200f, 0));
        grip.dispatchTouchEvent(MotionEvent.obtain(t, t + 80, MotionEvent.ACTION_UP, 200f, 100f, 0));
        settle();

        assertNull("nothing is left floating", SlothShell.mislay());
        assertEquals("and the list went with the drawer", View.GONE, picker.getVisibility());
    }

    @Test
    public void screenPrivacyIsOffOnceAndStaysOffIfYouTurnItBackOn() {
        TestActivity activity = build();
        assertTrue("the browser starts with the guard on", activity.settings.screenGuard);

        assertTrue("the shell clears it", SlothShell.guardOff(activity));
        assertFalse("and it is off", activity.settings.screenGuard);
        assertEquals("the clear is saved, not just held in memory", 1, activity.settingsSaved);

        // turning it back on by hand must survive the next launch
        activity.settings.screenGuard = true;
        assertFalse("the one-time clear does not run twice", SlothShell.guardOff(activity));
        assertTrue("so the choice sticks", activity.settings.screenGuard);
    }

    @Test
    public void typingAnAddressActuallyNavigates() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();

        // the browser's own handler is private, which is exactly why this failed silently
        android.widget.EditText field = new android.widget.EditText(activity);
        field.setText("google.com");
        DrawerFace.last = null;

        assertTrue("the navigation routine was reached", SlothShell.navigate(activity, field));
        assertEquals("and it was given the address", "google.com", DrawerFace.last);
    }

    @Test
    public void navigatingPutsTheSettingsPageAway() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        SlothShell.openSettingsPage(activity);
        settle();
        View page = findByViewTag(overlayOf(activity), "settings-page");
        assertEquals("the page is up to begin with", View.VISIBLE, page.getVisibility());

        EditText field = new EditText(activity);
        field.setText("google.com");
        assertTrue(SlothShell.navigate(activity, field));
        settle();

        // a full-screen layer left up covers the page that was just asked for and eats its taps
        assertEquals("and gone once something navigates", View.GONE, page.getVisibility());
        assertEquals("and the drawer is out of the way too", 0, drawerOf(activity).getLayoutParams().height);
    }

    @Test
    public void aCloseDuringTheOpeningAnimationStaysClosed() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        View bar = findByViewTag(overlayOf(activity), "drag");
        drag(bar, 40f, 420f);
        // the flick starts frames of its own; close while they are still arriving
        EditText field = new EditText(activity);
        field.setText("google.com");
        assertTrue(SlothShell.navigate(activity, field));
        settle();

        // un-cancelled frames used to put the drawer back over the page it had just left
        assertEquals("the drawer stays shut", 0, drawerOf(activity).getLayoutParams().height);
    }

    @Test
    public void aRebuildDuringAnAnimationDoesNotResurrectTheDrawer() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        View bar = findByViewTag(overlayOf(activity), "drag");
        drag(bar, 40f, 420f);
        SlothShell.rebuild(activity);
        settle();
        settle();

        assertEquals("a new surface starts shut", 0,
                drawerOf(activity).getLayoutParams().height);
    }

    @Test
    public void anInheritedSettingIsFoundAndWritten() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();

        // blockTrackers lives on the base class: getDeclaredField alone cannot see it
        assertTrue("an inherited switch still reads", SlothPrefs.flag(activity, "blockTrackers", false));
        assertTrue("and still writes", SlothPrefs.write(activity, "blockTrackers", false));
        assertFalse(activity.settings.blockTrackers);
    }

    @Test
    public void aLongSettingIsWrittenAsALong() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();

        // setInt on a long field throws IllegalArgumentException - the row would refuse
        assertEquals(100, SlothPrefs.number(activity, "textZoom", 0));
        assertTrue(SlothPrefs.write(activity, "textZoom", 125));
        assertEquals(125, activity.settings.textZoom);
    }

    @Test
    public void whatWentWrongSurvivesARestart() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        SlothLog.clear();
        SlothLog.note("a thing that went wrong");

        // a restart is a fresh process: same storage, empty memory
        java.lang.reflect.Field loaded = SlothLog.class.getDeclaredField("loaded");
        loaded.setAccessible(true);
        loaded.setBoolean(null, false);
        java.lang.reflect.Field lines = SlothLog.class.getDeclaredField("LINES");
        lines.setAccessible(true);
        ((java.util.ArrayDeque<?>) lines.get(null)).clear();

        SlothLog.attach(activity);

        assertTrue("the last session's problems are still readable",
                SlothLog.text().contains("a thing that went wrong"));
    }

    @Test
    public void navigatingFromAMirrorStillNavigates() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();

        EditText mirror = findAddress(overlayOf(activity));
        mirror.setText("google.com");
        DrawerFace.last = null;

        // with no real omnibox in reach the typed field is used as-is, not dropped
        assertNotNull(SlothShell.syncOmnibox(activity, mirror));
        assertTrue(SlothShell.navigate(activity, mirror));
        assertEquals("google.com", DrawerFace.last);
    }

    @Test
    public void aSwitchRowShowsASwitch() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        SlothShell.openSettingsPage(activity);
        settle();
        View page = findByViewTag(overlayOf(activity), "settings-page");

        View row = rowSaying(page, "Block trackers");
        assertNotNull("the row is there", row);
        assertNotNull("and it carries a switch, not just a word",
                findByViewTag(row, "switch-on"));

        tapChip(page, "Block trackers");
        settle();

        assertFalse(activity.settings.blockTrackers);
        assertNotNull("the switch moved", findByViewTag(row, "switch-off"));
        assertTrue("and a screen reader hears the state",
                row.getContentDescription().toString().endsWith(", off"));
    }

    /** The settings row whose label is this text. */
    private View rowSaying(View root, String label) {
        java.util.List<View> all = new java.util.ArrayList<>();
        collectTagged(root, "settings-row", all);
        for (View row : all) {
            if (String.valueOf(row.getContentDescription()).startsWith(label)) {
                return row;
            }
        }
        return null;
    }

    private void collectFaint(View v, java.util.List<View> into) {
        if (v.getVisibility() == View.VISIBLE && v.getAlpha() < 0.99f) {
            into.add(v);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                collectFaint(g.getChildAt(i), into);
            }
        }
    }

    private void collectTagged(View v, String tag, java.util.List<View> into) {
        if (tag.equals(v.getTag())) {
            into.add(v);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                collectTagged(g.getChildAt(i), tag, into);
            }
        }
    }

    @Test
    public void theProbeNamesWhateverCoversThePage() {
        TestActivity activity = build();
        SlothLog.clear();

        FrameLayout root = new FrameLayout(activity);
        org.mozilla.geckoview.GeckoView page = new org.mozilla.geckoview.GeckoView(activity);
        page.setId(4201);
        root.addView(page, new FrameLayout.LayoutParams(1080, 1600));
        View cover = new View(activity);
        cover.setTag("cover");
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(1080, 1200);
        clp.topMargin = 200;
        root.addView(cover, clp);
        activity.setContentView(root);
        layout(root, 1080, 1920);

        // the harness activity has no geckoView field yet: give it one
        activity.attachPage(page);

        SlothCover.probe(activity, "test");

        String log = SlothLog.text();
        assertTrue("the probe finds the page: " + log, !log.contains("no page view found"));
        assertTrue("and names what covers it: " + log, log.contains("over the page"));
        assertTrue("by its tag: " + log, log.contains("[cover]"));
    }

    @Test
    public void theProbeIsSilentWhenNothingCoversThePage() {
        TestActivity activity = build();
        SlothLog.clear();

        FrameLayout root = new FrameLayout(activity);
        org.mozilla.geckoview.GeckoView page = new org.mozilla.geckoview.GeckoView(activity);
        root.addView(page, new FrameLayout.LayoutParams(1080, 1600));
        activity.setContentView(root);
        layout(root, 1080, 1920);
        activity.attachPage(page);

        SlothCover.probe(activity, "test");

        assertFalse("nothing to say about a clear page: " + SlothLog.text(),
                SlothLog.text().contains("over the page"));
    }

    @Test
    public void aPanelPushedOffTheScreenIsNotAccusedOfCoveringThePage() {
        TestActivity activity = build();
        SlothLog.clear();

        FrameLayout root = new FrameLayout(activity);
        org.mozilla.geckoview.GeckoView page = new org.mozilla.geckoview.GeckoView(activity);
        root.addView(page, new FrameLayout.LayoutParams(1080, 1600));
        View panel = new View(activity);
        panel.setTag("panel");
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(1080, 1200);
        plp.topMargin = 200;
        root.addView(panel, plp);
        activity.setContentView(root);
        layout(root, 1080, 1920);
        activity.attachPage(page);

        // the browser parks its panel below the screen when it is closed
        panel.setTranslationY(1920f);
        SlothCover.probe(activity, "parked");
        assertFalse("a panel off the screen covers nothing: " + SlothLog.text(),
                SlothLog.text().contains("[panel]"));

        // and when it is really over the page, the report says what it is
        SlothLog.clear();
        panel.setTranslationY(0f);
        panel.setAlpha(0.9f);
        SlothCover.probe(activity, "up");
        String log = SlothLog.text();
        assertTrue("a panel on top is named: " + log, log.contains("[panel]"));
        assertTrue("with its alpha: " + log, log.contains("a=0.9"));
        assertTrue("and whether it paints: " + log, log.contains("bg=none"));
    }

    private void layout(View root, int w, int h) {
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                layout(g.getChildAt(i), g.getChildAt(i).getLayoutParams().width,
                        g.getChildAt(i).getLayoutParams().height);
            }
        }
    }

    @Test
    public void theSurfaceIsOnlyAsTallAsTheBarAtRest() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();

        View surface = findViewById(overlayOf(activity), "surface");
        int height = surface.getLayoutParams().height;

        // a wrapping surface measures its MATCH_PARENT backdrop against the whole window,
        // and the painted scene then covers the page until a drawer height is set once
        assertTrue("the surface has a real height, not wrap: " + height,
                height > 0);
        assertTrue("and it is the bar, not the page: " + height, height < 400);
    }

    @Test
    public void theSurfaceGrowsWithTheDrawer() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        int rest = findViewById(overlayOf(activity), "surface").getLayoutParams().height;

        drag(findByViewTag(overlayOf(activity), "drag"), 40f, 900f);
        settle();

        int open = findViewById(overlayOf(activity), "surface").getLayoutParams().height;
        assertTrue("the surface grows with the drawer: " + rest + " -> " + open, open > rest);
    }

    /** The first view under this root carrying a tag. */
    private View findViewById(View v, String tag) {
        if (tag.equals(v.getTag())) {
            return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View found = findViewById(g.getChildAt(i), tag);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    public void theAppPanelIsNeverLentOutAgain() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();

        // the app's own panel was the empty box over the page: bordered, visible, blank
        View panel = new FrameLayout(activity);
        panel.setTag("panel");
        SlothShell.releasePanel(activity);

        SlothLog.clear();
        SlothShell.openPanel(activity, "settings");
        settle();

        View page = findByViewTag(overlayOf(activity), "settings-page");
        assertEquals("a panel request lands on our page", View.VISIBLE, page.getVisibility());
        assertTrue("and it says so: " + SlothLog.text(),
                SlothLog.text().contains("panel settings opened on our page"));
    }

    @Test
    public void aListTheAppDoesNotDrawIsClosedAgain() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        SlothLog.clear();

        SlothShell.openPanel(activity, "bookmarks");
        settle();
        // a lent panel the app left empty is the bordered box; the loan is provisional
        assertEquals("the panel is shut again", View.GONE,
                activity.binding.panel.getVisibility());
        assertTrue("and it says which screen came up blank: " + SlothLog.text(),
                SlothLog.text().contains("nothing drew in the panel for bookmarks"));
        assertEquals("the page is up instead", View.VISIBLE,
                findByViewTag(overlayOf(activity), "settings-page").getVisibility());
    }

    @Test
    public void openingTheDrawerEndsALoan() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();

        SlothShell.openPanel(activity, "history");
        drag(findByViewTag(overlayOf(activity), "drag"), 40f, 700f);
        settle();

        assertEquals("the panel goes back with the drawer", View.GONE,
                activity.binding.panel.getVisibility());
    }

    @Test
    public void thePageStartsWhereTheBarEnds() {
        // measured, not guessed: the surface's bottom edge minus the page root's top edge
        assertEquals("no gap, no overlap", 184, SlothShell.pagePaddingFor(56, 240));
        // a page root already padded for the status bar must not be counted twice
        assertEquals("a padded root is not padded twice", 128,
                SlothShell.pagePaddingFor(112, 240));
        assertEquals("and a surface above the root cannot pull it up",
                0, SlothShell.pagePaddingFor(240, 100));
    }

    @Test
    public void theSceneSlidesAsTheDrawerComesOut() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        SlothBackdrop scene = (SlothBackdrop) findViewById(overlayOf(activity), "backdrop");
        assertNotNull("the painted scene is there", scene);
        assertEquals("and sits still while the drawer is shut", 0f, scene.depth(), 0.001f);

        drag(findByViewTag(overlayOf(activity), "drag"), 40f, 900f);
        settle();

        assertTrue("and travels with the drawer: " + scene.depth(), scene.depth() > 0.1f);
        assertTrue("without going past its travel", scene.depth() <= 1f);
    }

    @Test
    public void everyCardHasArrivedByTheTimeTheDrawerIsOpen() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();

        drag(findByViewTag(overlayOf(activity), "drag"), 40f, 900f);
        settle();

        // nothing in an open drawer may be left half-arrived
        java.util.List<View> faint = new java.util.ArrayList<>();
        collectFaint(drawerOf(activity), faint);
        assertTrue("nothing is left mid-entrance: " + faint, faint.isEmpty());
    }

    @Test
    public void theSceneActuallyMovesWhenTheDrawerComesOut() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        SlothBackdrop scene = new SlothBackdrop(activity);
        int w = 900;
        int h = 700;
        scene.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        scene.layout(0, 0, w, h);

        android.graphics.Bitmap still = android.graphics.Bitmap.createBitmap(
                w, h, android.graphics.Bitmap.Config.ARGB_8888);
        scene.draw(new android.graphics.Canvas(still));

        scene.setDepth(1f);
        android.graphics.Bitmap moved = android.graphics.Bitmap.createBitmap(
                w, h, android.graphics.Bitmap.Config.ARGB_8888);
        scene.draw(new android.graphics.Canvas(moved));

        int differing = 0;
        for (int y = 0; y < h; y += 4) {
            for (int x = 0; x < w; x += 4) {
                if (still.getPixel(x, y) != moved.getPixel(x, y)) {
                    differing++;
                }
            }
        }
        // most of the scene is a smooth gradient, so only its edges can differ - but they
        // must, or the parallax is a field nobody reads
        assertTrue("the scene travels: " + differing + " sampled pixels moved", differing > 250);
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test
    public void theBarControlsAreDrawnRatherThanTyped() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();

        View bar = findByViewTag(overlayOf(activity), "drag");
        assertNotNull("the bar is there", bar);
        java.util.List<View> drawn = new java.util.ArrayList<>();
        collectKind(bar, SlothIcons.class, drawn);
        assertEquals("back, forward, reload, plus, menu, the lock and the tab stack: " + drawn, 7, drawn.size());

        // and no glyph is left behind them: the old bars were text characters
        java.util.List<View> glyphs = new java.util.ArrayList<>();
        collectTextWithLabel(bar, "Back", glyphs);
        collectTextWithLabel(bar, "Menu", glyphs);
        assertTrue("the glyph controls are gone: " + glyphs, glyphs.isEmpty());

        // and each of them has to put pixels on the canvas, or it is an empty button
        for (View icon : drawn) {
            int px = Math.max(1, icon.getLayoutParams().width);
            if (px <= 0) {
                px = Math.round(30 * activity.getResources().getDisplayMetrics().density);
            }
            icon.measure(View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY));
            icon.layout(0, 0, px, px);
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                    px, px, android.graphics.Bitmap.Config.ARGB_8888);
            icon.draw(new android.graphics.Canvas(bitmap));
            int lit = 0;
            for (int y = 0; y < px; y += 2) {
                for (int x = 0; x < px; x += 2) {
                    if ((bitmap.getPixel(x, y) >>> 24) > 24) {
                        lit++;
                    }
                }
            }
            // a thin stroke sampled every other pixel lands on a handful of samples; zero
            // would be an icon that is not there at all. The 9 dp tab stack is smaller than
            // the sampling grid allows for, so it is held to "something was painted"
            int wanted = px < Math.round(16 * activity.getResources().getDisplayMetrics().density)
                    ? 2 : 10;
            assertTrue("the icon is drawn, not blank: " + lit + " lit pixels", lit >= wanted);
        }
    }

    /** Three open tabs, the first current - the strip the peek mirrors. */
    private LinearLayout openTabs(TestActivity activity) {
        LinearLayout strip = new LinearLayout(activity);
        for (int i = 0; i < 3; i++) {
            android.widget.TextView chip = new android.widget.TextView(activity);
            chip.setText(i == 0 ? "google.com" : (i == 1 ? "news" : "docs"));
            chip.setTag("tab");
            chip.setSelected(i == 0);
            strip.addView(chip);
        }
        activity.binding.slotTabs = strip;
        return strip;
    }

    private View findTextContaining(View v, String needle) {
        if (v instanceof android.widget.TextView
                && ((android.widget.TextView) v).getText().toString().contains(needle)) {
            return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View hit = findTextContaining(g.getChildAt(i), needle);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    private View findText(View v, String text) {
        if (v instanceof android.widget.TextView
                && text.contentEquals(((android.widget.TextView) v).getText())) {
            return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View hit = findText(g.getChildAt(i), text);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    private void collectTextWithLabel(View v, String label, java.util.List<View> into) {
        if (v instanceof android.widget.TextView
                && label.equals(String.valueOf(v.getContentDescription()))) {
            into.add(v);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                collectTextWithLabel(g.getChildAt(i), label, into);
            }
        }
    }

    private void collectKind(View v, Class<?> type, java.util.List<View> into) {
        if (type.isInstance(v)) {
            into.add(v);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                collectKind(g.getChildAt(i), type, into);
            }
        }
    }

    @Test
    public void thePeekIsAStripOfTabsNotASmallDashboard() throws Exception {
        TestActivity activity = build();
        openTabs(activity);
        SlothShell.own(activity);
        settle();

        // a short pull lands on the measured peek seat, which is what this is about
        driveHeight(activity, peekHeight(activity));
        settle();

        View strip = findByViewTag(drawerOf(activity), "peek-strip");
        assertNotNull("the peek strip is in the drawer", strip);
        assertEquals("and it is what the peek shows", View.VISIBLE, strip.getVisibility());

        View close = findByViewTag(drawerOf(activity), "peek-close");
        assertNotNull("the peek carries its own close", close);
        assertNotNull("and a tile per open tab",
                findByViewTag(drawerOf(activity), "peek-tab"));
        assertNotNull("and a way to open another", findByViewTag(drawerOf(activity), "peek-new"));
    }

    @Test
    public void theFullSheetPutsTheStripAway() throws Exception {
        TestActivity activity = build();
        openTabs(activity);
        SlothShell.own(activity);
        settle();

        driveHeight(activity, peekHeight(activity) + 400);
        settle();

        View strip = findByViewTag(drawerOf(activity), "peek-strip");
        assertNotNull(strip);
        assertEquals("the strip belongs to the peek only", View.GONE, strip.getVisibility());
        View tabs = findText(drawerOf(activity), "Tabs");
        assertNotNull("and the full sheet's own sections are back", tabs);
        assertEquals(View.VISIBLE, tabs.getVisibility());
    }

    @Test
    public void thePeeksCloseControlShutsTheDrawer() throws Exception {
        TestActivity activity = build();
        openTabs(activity);
        SlothShell.own(activity);
        settle();

        driveHeight(activity, peekHeight(activity));
        settle();
        assertTrue("the peek is open",
                ((ViewGroup.LayoutParams) drawerOf(activity).getLayoutParams()).height > 0);

        findByViewTag(drawerOf(activity), "peek-close").performClick();
        settle();

        assertEquals("and its close shuts it", 0,
                ((ViewGroup.LayoutParams) drawerOf(activity).getLayoutParams()).height);
    }

    @Test
    public void theDiagnosticsPageReportsWhatTheShellCanSee() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();

        SlothShell.openDiagPage(activity);
        settle();

        View page = findByViewTag(overlayOf(activity), "diag-page");
        assertNotNull("the report page is in the window", page);
        assertEquals(View.VISIBLE, page.getVisibility());
        View report = findTextContaining(page, "sloth diagnostics");
        assertNotNull("and it carries the report", report);
        String text = ((android.widget.TextView) report).getText().toString();
        assertTrue("it says how big the frame is: " + text, text.contains("window:"));
        assertTrue("and where the seats sit", text.contains("seats:"));
        assertTrue("and what the page is doing", text.contains("page:"));
        assertTrue("and whether a session is attached", text.contains("page session:"));
    }

    @Test
    public void thePanelCountSeesAListDrawnInsideAScroller() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();

        View panel = SlothShell.resolve(activity, "panelBody");
        assertNotNull(panel);
        int empty = SlothShell.panelContentCount(activity);

        android.widget.ScrollView scroller = new android.widget.ScrollView(activity);
        LinearLayout rows = new LinearLayout(activity);
        for (int i = 0; i < 3; i++) {
            android.widget.TextView row = new android.widget.TextView(activity);
            row.setText("bookmark " + i);
            rows.addView(row);
        }
        scroller.addView(rows);
        ((ViewGroup) panel).addView(scroller);

        int filled = SlothShell.panelContentCount(activity);
        assertTrue("a list inside a scroller counts: " + empty + " -> " + filled,
                filled >= empty + 4);
    }

    @Test
    public void theReportCanBeSavedToTheAppStorage() {
        TestActivity activity = build();
        SlothLog.note("a line worth keeping");
        String path = SlothLog.save(activity);
        assertNotNull("the report is written somewhere", path);
        java.io.File file = new java.io.File(path);
        assertTrue("and the file is there", file.exists() && file.length() > 0);
    }

    @Test
    public void backClosesOurPagesBeforeItLeavesTheBrowser() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();

        assertFalse("nothing open, so back belongs to the browser",
                SlothShell.backOneLayer(activity));

        SlothShell.openSettingsPage(activity);
        settle();
        assertTrue("back closes settings", SlothShell.backOneLayer(activity));
        assertEquals(View.GONE,
                findByViewTag(overlayOf(activity), "settings-page").getVisibility());

        SlothShell.openDiagPage(activity);
        settle();
        assertTrue("back closes diagnostics", SlothShell.backOneLayer(activity));
        assertEquals(View.GONE,
                findByViewTag(overlayOf(activity), "diag-page").getVisibility());

        driveHeight(activity, peekHeight(activity));
        settle();
        assertTrue("and back closes an open drawer", SlothShell.backOneLayer(activity));
        settle();
        assertEquals(0,
                ((ViewGroup.LayoutParams) drawerOf(activity).getLayoutParams()).height);
    }

    @Test
    public void aPageLeftAtZeroHeightIsGivenItsHeightBack() {
        TestActivity activity = build();
        SlothLog.clear();

        FrameLayout root = new FrameLayout(activity);
        org.mozilla.geckoview.GeckoView page = new org.mozilla.geckoview.GeckoView(activity);
        page.setId(4301);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(1280, 0);
        root.addView(page, lp);
        activity.setContentView(root);
        layout(root, 1280, 2772);
        activity.attachPage(page);

        SlothShell.ensurePageVisible(activity);

        assertEquals("the page is given the window's height back",
                ViewGroup.LayoutParams.MATCH_PARENT, page.getLayoutParams().height);
        assertTrue("and it says so: " + SlothLog.text(),
                SlothLog.text().contains("page was 0px tall"));
    }

    @Test
    public void backAlsoPutsAwayTheBrowsersOwnPanel() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();

        // the loan is checked a moment later, so back has to work while it is still up
        SlothShell.openPanel(activity, "bookmarks");
        assertTrue("the loan is up", SlothShell.settingsOpenForTest());

        assertTrue("back answers it", SlothShell.backOneLayer(activity));
        settle();

        assertFalse("and the loan is over", SlothShell.settingsOpenForTest());
        assertEquals("with the panel gone", View.GONE,
                SlothShell.resolve(activity, "panel").getVisibility());
    }

    @Test
    public void closingMidEntranceLeavesTheCardsInNotHalfFaded() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();

        driveHeight(activity, peekHeight(activity) + 400);
        // one frame in, the cards are mid-entrance
        org.robolectric.shadows.ShadowLooper.idleMainLooper(16,
                java.util.concurrent.TimeUnit.MILLISECONDS);

        // now shut it, which bumps the generation under the entrance
        SlothShell.backOneLayer(activity);
        settle();

        View board = SlothShell.resolve(activity, "board");
        assertNotNull(board);
        for (int i = 0; i < ((ViewGroup) board).getChildCount(); i++) {
            View card = ((ViewGroup) board).getChildAt(i);
            assertEquals("a cancelled entrance still finishes the cards",
                    1f, card.getAlpha(), 0.01f);
            assertEquals(0f, card.getTranslationY(), 0.01f);
        }
    }

    @Test
    public void releasingTheShellForgetsThePeekAndTheScene() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        SlothShell.openPanel(activity, "bookmarks");

        SlothShell.releaseShell();

        assertFalse("a dead window keeps no page", SlothShell.backOneLayer(activity));
    }

    @Test
    public void theBarFollowsThePageWithoutBeingTouched() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();

        // the page navigates itself: the browser writes its own omnibox
        ((android.widget.EditText) activity.binding.urlBar).setText("example.org/leaf");
        assertFalse("the mirror is behind to begin with",
                findText(overlayOf(activity), "example.org/leaf") != null);

        // the tick is the shell's own timer, not a tap
        org.robolectric.shadows.ShadowLooper.idleMainLooper(900,
                java.util.concurrent.TimeUnit.MILLISECONDS);

        assertNotNull("the bar catches up on its own",
                findText(overlayOf(activity), "example.org/leaf"));
    }

    @Test
    public void theTabCountFollowsTheTabsWithoutBeingTouched() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();

        for (int i = 0; i < 4; i++) {
            android.widget.TextView chip = new android.widget.TextView(activity);
            chip.setText("tab " + i);
            chip.setTag("tab");
            activity.binding.slotTabs.addView(chip);
        }

        org.robolectric.shadows.ShadowLooper.idleMainLooper(900,
                java.util.concurrent.TimeUnit.MILLISECONDS);

        assertNotNull("the badge counts what the browser has",
                findText(overlayOf(activity), String.valueOf(
                        activity.binding.slotTabs.getChildCount())));
    }

    @Test
    public void holdingAPeekSquareClosesThatTab() throws Exception {
        TestActivity activity = build();
        // chips the way the browser builds them: a title and its own close control
        LinearLayout strip = new LinearLayout(activity);
        strip.setTag("tab-squares");
        for (int i = 0; i < 3; i++) {
            LinearLayout chip = new LinearLayout(activity);
            android.widget.TextView title = new android.widget.TextView(activity);
            title.setText(i == 0 ? "google.com" : (i == 1 ? "news" : "docs"));
            chip.addView(title);
            chip.setTag("tab");
            chip.setSelected(i == 0);
            View close = new View(activity);
            close.setTag("close");
            close.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    activity.binding.slotTabs.removeView((View) v.getParent());
                }
            });
            chip.addView(close);
            strip.addView(chip);
        }
        activity.binding.slotTabs = strip;

        SlothShell.own(activity);
        settle();
        driveHeight(activity, peekHeight(activity));
        settle();

        View row = ((ViewGroup) findByViewTag(drawerOf(activity), "peek-strip")).getChildAt(0);
        View firstTile = ((ViewGroup) row).getChildAt(1);
        assertTrue("the tile takes a long press", firstTile.performLongClick());
        settle();

        assertEquals("one tab fewer", 2, activity.binding.slotTabs.getChildCount());
        View rowAfter = ((ViewGroup) findByViewTag(drawerOf(activity), "peek-strip")).getChildAt(0);
        assertEquals("and the strip refilled itself", 4, ((ViewGroup) rowAfter).getChildCount());
    }

    @Test
    public void theStripClosesTheSheetOnAVerticalDrag() throws Exception {
        TestActivity activity = build();
        openTabs(activity);
        SlothShell.own(activity);
        settle();
        driveHeight(activity, peekHeight(activity));
        settle();

        View strip = findByViewTag(drawerOf(activity), "peek-strip");
        long t = SystemClock.uptimeMillis();
        strip.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200f, 120f, 0));
        for (int i = 1; i <= 5; i++) {
            strip.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 14L,
                    MotionEvent.ACTION_MOVE, 200f, 120f - i * 60f, 0));
        }
        strip.dispatchTouchEvent(MotionEvent.obtain(t, t + 80L,
                MotionEvent.ACTION_UP, 200f, -180f, 0));
        settle();

        assertEquals("an upward drag on the strip closes the sheet", 0,
                ((ViewGroup.LayoutParams) drawerOf(activity).getLayoutParams()).height);
    }

    @Test
    public void theStripKeepsItsSidewaysGesture() throws Exception {
        TestActivity activity = build();
        openTabs(activity);
        SlothShell.own(activity);
        settle();
        driveHeight(activity, peekHeight(activity));
        settle();
        int before = ((ViewGroup.LayoutParams) drawerOf(activity).getLayoutParams()).height;

        View strip = findByViewTag(drawerOf(activity), "peek-strip");
        long t = SystemClock.uptimeMillis();
        strip.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 100f, 120f, 0));
        for (int i = 1; i <= 5; i++) {
            strip.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 14L,
                    MotionEvent.ACTION_MOVE, 100f + i * 70f, 122f, 0));
        }
        strip.dispatchTouchEvent(MotionEvent.obtain(t, t + 80L,
                MotionEvent.ACTION_UP, 450f, 124f, 0));
        settle();

        assertEquals("a sideways flick scrolls the tabs, it does not move the sheet",
                before, ((ViewGroup.LayoutParams) drawerOf(activity).getLayoutParams()).height);
    }

    @Test
    public void theTickPutsABlackedOutPageBackWithoutBeingAsked() throws Exception {
        TestActivity activity = build();
        SlothLog.clear();

        FrameLayout root = new FrameLayout(activity);
        org.mozilla.geckoview.GeckoView page = new org.mozilla.geckoview.GeckoView(activity);
        page.setId(4401);
        root.addView(page, new FrameLayout.LayoutParams(1280, 0));
        activity.setContentView(root);
        layout(root, 1280, 2772);
        activity.attachPage(page);

        SlothShell.own(activity);
        settle();
        org.robolectric.shadows.ShadowLooper.idleMainLooper(900,
                java.util.concurrent.TimeUnit.MILLISECONDS);

        assertEquals("the page is back to full height",
                ViewGroup.LayoutParams.MATCH_PARENT, page.getLayoutParams().height);
        assertTrue("and it says why: " + SlothLog.text(),
                SlothLog.text().contains("page was 0px tall"));
    }

    @Test
    public void theCachedPageBelongsToOneWindow() {
        TestActivity first = build();
        SlothShell.own(first);
        settle();
        org.mozilla.geckoview.GeckoView page = new org.mozilla.geckoview.GeckoView(first);
        first.setContentView(page);
        first.attachPage(page);
        assertTrue("the page is found and remembered", page == SlothCover.page(first));

        TestActivity second = build();
        assertNull("another window does not inherit it", SlothCover.page(second));
    }

    @Test
    public void aSidewaysFlickOnTheBarChangesTab() {
        TestActivity activity = build();
        openTabs(activity);          // three chips, the first current
        final Counter[] pressed = new Counter[3];
        for (int i = 0; i < 3; i++) {
            pressed[i] = new Counter();
            activity.binding.slotTabs.getChildAt(i).setOnClickListener(pressed[i]);
        }
        SlothShell.own(activity);
        settle();

        View bar = findByViewTag(overlayOf(activity), "drag");
        long t = SystemClock.uptimeMillis();
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 900f, 80f, 0));
        for (int i = 1; i <= 6; i++) {
            bar.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 12L,
                    MotionEvent.ACTION_MOVE, 900f - i * 90f, 82f, 0));
        }
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t + 80L,
                MotionEvent.ACTION_UP, 360f, 84f, 0));
        settle();

        assertEquals("the tab to the right is chosen", 1, pressed[1].clicks);
        assertEquals("and only that one", 0, pressed[0].clicks + pressed[2].clicks);
        assertEquals("the drawer did not open", 0,
                ((ViewGroup.LayoutParams) drawerOf(activity).getLayoutParams()).height);
    }

    @Test
    public void aFlickPastTheLastTabDoesNothing() {
        TestActivity activity = build();
        openTabs(activity);
        final Counter third = new Counter();
        activity.binding.slotTabs.getChildAt(2).setOnClickListener(third);
        activity.binding.slotTabs.getChildAt(2).setSelected(true); // on the last tab
        SlothShell.own(activity);
        settle();

        View bar = findByViewTag(overlayOf(activity), "drag");
        long t = SystemClock.uptimeMillis();
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 900f, 80f, 0));
        for (int i = 1; i <= 6; i++) {
            bar.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 12L,
                    MotionEvent.ACTION_MOVE, 900f - i * 90f, 82f, 0));
        }
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t + 80L,
                MotionEvent.ACTION_UP, 360f, 84f, 0));
        settle();

        assertEquals("it stops at the end rather than wrapping", 0, third.clicks);
    }

    @Test
    public void theAddressDotAndCopyRow() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();

        ((android.widget.EditText) activity.binding.urlBar).setText("https://sloth.example/leaf");
        org.robolectric.shadows.ShadowLooper.idleMainLooper(900,
                java.util.concurrent.TimeUnit.MILLISECONDS);
        settle();

        View row = findByViewTag(drawerOf(activity), "site-row");
        assertNotNull("the address row exists", row);
        assertTrue("and it is a control", row.isClickable());
        ViewGroup rowGroup = (ViewGroup) row;
        assertTrue("that says what it does",
                String.valueOf(rowGroup.getChildAt(1).getContentDescription()).contains("Tap to copy"));

        row.performClick();
        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) activity.getSystemService(
                        android.content.Context.CLIPBOARD_SERVICE);
        assertEquals("the address is on the clipboard",
                "https://sloth.example/leaf",
                String.valueOf(clipboard.getPrimaryClip().getItemAt(0).coerceToText(activity)));
        assertEquals("and it said so", "Address copied",
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void theSeamGrowsAsTheSheetOpens() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        // the hinge line is gone: the seam is the surface itself, and one shape with one
        // fill needs no drawn mark to explain where the bar ends and the dashboard begins
        assertNull("no hinge line inside the sheet", findByViewTag(drawerOf(activity), "seam"));
        View surface = findByViewTag(overlayOf(activity), "surface");
        assertNotNull("the surface holds the fill", surface.getBackground());

        driveHeight(activity, peekHeight(activity));
        settle();
        assertNotNull("still one fill open", surface.getBackground());
        assertEquals("the bar draws no fill of its own, open or shut", null,
                findByViewTag(overlayOf(activity), "bar").getBackground());
    }

    @Test
    public void theFieldStepsBackWithTheBar() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        View field = findAddress(overlayOf(activity));
        assertNotNull(field);
        View pill = (View) field.getParent();
        assertEquals("closed, the field is at full weight", 1f, pill.getAlpha(), 0.01f);

        driveHeight(activity, peekHeight(activity));
        settle();
        assertEquals("open, the field is still part of the header and still readable: "
                + pill.getAlpha(), 1f, pill.getAlpha(), 0.01f);
        driveHeight(activity, 0);
        settle();
        assertEquals("and it stays that way shut", 1f, pill.getAlpha(), 0.01f);
    }

    @Test
    public void theSheetIsOutlinedSoTheSystemCanCastItsShadow() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        View surface = findByViewTag(overlayOf(activity), "surface");
        assertNotNull(surface);
        assertTrue("the panel asks for elevation: " + surface.getElevation(),
                surface.getElevation() > 0);

        // an outline is what the system draws the shadow around; without one, elevation
        // is a number with nothing to cast it
        android.graphics.Outline outline = new android.graphics.Outline();
        android.view.ViewOutlineProvider provider = surface.getOutlineProvider();
        assertNotNull("the panel has an outline provider", provider);
        surface.layout(0, 0, 1080, 640);
        provider.getOutline(surface, outline);
        assertTrue("and it covers the panel", outline.getRect(
                new android.graphics.Rect()));
    }

    @Test
    public void aDragThatStartsOnTheAddressFieldStillOpensTheDrawer() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();

        View bar = findByViewTag(overlayOf(activity), "drag");
        EditText field = findAddress(overlayOf(activity));
        int[] fieldAt = new int[2];
        field.getLocationOnScreen(fieldAt);
        int[] barAt = new int[2];
        bar.getLocationOnScreen(barAt);
        float x = fieldAt[0] - barAt[0] + 20;
        float y = fieldAt[1] - barAt[1] + field.getHeight() / 2f;

        long t = SystemClock.uptimeMillis();
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0));
        for (int i = 1; i <= 6; i++) {
            bar.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 14L,
                    MotionEvent.ACTION_MOVE, x, y + i * 60f, 0));
        }
        settle();

        assertTrue("the field is not a dead zone for the drag: " + heightOf(drawerOf(activity)),
                heightOf(drawerOf(activity)) > 0);
        assertFalse("and the drag did not leave the field in edit mode", field.hasFocus());
    }

    @Test
    public void theSheetDoesNotJumpByTheSlopWhenGrabbed() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        View bar = findByViewTag(overlayOf(activity), "drag");

        long t = SystemClock.uptimeMillis();
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 500f, 40f, 0));
        // three moves, the last one just over the touch slop
        int[] steps = {6, 12, 20};
        for (int i = 0; i < steps.length; i++) {
            bar.dispatchTouchEvent(MotionEvent.obtain(t, t + (i + 1) * 16L,
                    MotionEvent.ACTION_MOVE, 500f, 40f + steps[i], 0));
        }
        settle();

        // the sheet is under the finger: as far as the finger went, never further
        int height = heightOf(drawerOf(activity));
        assertTrue("no jump at the grab: " + height + " px for 20 px of finger",
                height <= px(22));
        assertTrue("and it did move with the finger: " + height, height >= px(4));
    }

    @Test
    public void theSheetFollowsTheFingerPastTheContentHeight() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        View bar = findByViewTag(overlayOf(activity), "drag");

        long t = SystemClock.uptimeMillis();
        bar.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 500f, 40f, 0));
        int previous = 0;
        for (int i = 1; i <= 20; i++) {
            bar.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 14L,
                    MotionEvent.ACTION_MOVE, 500f, 40f + i * 40f, 0));
            settle();
            int now = heightOf(drawerOf(activity));
            assertTrue("the sheet never runs backwards under the finger: " + previous
                    + " then " + now + " at step " + i, now >= previous - px(2));
            previous = now;
        }
        assertTrue("and it kept following: " + previous, previous > px(200));
    }

    @Test
    public void grabbingTheSheetStopsAFlickInFlight() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        View bar = findByViewTag(overlayOf(activity), "drag");

        // a flick up from closed: the snap starts flying towards the peek
        long t0 = SystemClock.uptimeMillis();
        bar.dispatchTouchEvent(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, 500f, 200f, 0));
        for (int i = 1; i <= 6; i++) {
            bar.dispatchTouchEvent(MotionEvent.obtain(t0, t0 + i * 10L,
                    MotionEvent.ACTION_MOVE, 500f, 200f - i * 50f, 0));
        }
        bar.dispatchTouchEvent(MotionEvent.obtain(t0, t0 + 70L,
                MotionEvent.ACTION_UP, 500f, -100f, 0));
        // one frame later the finger lands again and pulls the sheet back down
        org.robolectric.shadows.ShadowLooper.idleMainLooper(32,
                java.util.concurrent.TimeUnit.MILLISECONDS);
        long t1 = SystemClock.uptimeMillis();
        bar.dispatchTouchEvent(MotionEvent.obtain(t1, t1, MotionEvent.ACTION_DOWN, 500f, 300f, 0));
        for (int i = 1; i <= 6; i++) {
            bar.dispatchTouchEvent(MotionEvent.obtain(t1, t1 + i * 14L,
                    MotionEvent.ACTION_MOVE, 500f, 300f - i * 90f, 0));
        }
        settle();

        assertEquals("the animation let go of the height", 0, heightOf(drawerOf(activity)));
    }

    @Test
    public void thePeekDoesNotFlickerAtItsBoundary() throws Exception {
        TestActivity activity = build();
        openTabs(activity);
        SlothShell.own(activity);
        settle();
        int peek = peekHeight(activity);

        // just above the strip: not the peek yet
        driveHeight(activity, peek + px(30));
        settle();
        assertEquals("no strip above the threshold", View.GONE, findByViewTag(
                drawerOf(activity), "peek-strip").getVisibility());

        // at the strip it becomes the peek
        driveHeight(activity, peek + px(4));
        settle();
        View strip = findByViewTag(drawerOf(activity), "peek-strip");
        assertNotNull(strip);
        assertEquals("the strip is showing", View.VISIBLE, strip.getVisibility());

        // and it stays the peek while the drag wanders back up: hysteresis, not a race
        driveHeight(activity, peek + px(30));
        settle();
        assertEquals("still the strip", View.VISIBLE, findByViewTag(drawerOf(activity),
                "peek-strip").getVisibility());

        // far enough up and it hands back to the dashboard
        driveHeight(activity, peek + px(80));
        settle();
        assertEquals("the strip steps out", View.GONE, findByViewTag(drawerOf(activity),
                "peek-strip").getVisibility());
    }

    @Test
    public void theDashboardHeaderNamesThePageYouAreOn() {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        ((android.widget.EditText) activity.binding.urlBar).setText("https://allrecipes.com/recipe/1");
        org.robolectric.shadows.ShadowLooper.idleMainLooper(900,
                java.util.concurrent.TimeUnit.MILLISECONDS);
        settle();

        String shown = dumpText(overlayOf(activity));
        assertTrue("the panel's own line names the page: " + shown,
                shown.contains("allrecipes.com"));
    }

    @Test
    public void theDashboardClosesWithADrawnCross() throws Exception {
        TestActivity activity = build();
        SlothShell.own(activity);
        settle();
        // the way out is the strip's close square and a tap on the header; a second close
        // button inside the panel was part of the "two surfaces" furniture
        assertNull("no second close control in the panel",
                findTagged(overlayOf(activity), "Close the dashboard"));

        driveHeight(activity, peekHeight(activity));
        settle();
        View strip = findByViewTag(overlayOf(activity), "peek-strip");
        assertNotNull("the strip", strip);
        java.util.List<View> drawn = new java.util.ArrayList<>();
        collectKind((ViewGroup) strip, SlothIcons.class, drawn);
        boolean hasCross = false;
        for (View view : drawn) {
            hasCross |= ((SlothIcons) view).kind() == SlothIcons.Kind.CLOSE;
        }
        assertTrue("the strip closes the drawer with a drawn cross", hasCross);
    }

    /* --------------------------------------------------------------- helpers */

    private EditText findAddress(View v) {
        if (v instanceof EditText) {
            return (EditText) v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                EditText found = findAddress(g.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private View findChip(View v, String label) {
        if (v instanceof android.widget.TextView && label.contentEquals(((android.widget.TextView) v).getText())) {
            return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View found = findChip(g.getChildAt(i), label);
                if (found != null) {
                    return found;
                }
            }
        }
        if (label.equals(String.valueOf(v.getContentDescription()))) {
            return v;
        }
        return null;
    }

    /** A tab card holds its title in the first TextView inside it. */
    private String tabLabel(View card) {
        final String[] out = new String[]{""};
        walk(card, new ViewVisitor() {
            @Override
            public void visit(View v) {
                // the card holds both an initial and the title: the title is the longer one
                if (v instanceof android.widget.TextView) {
                    String text = ((android.widget.TextView) v).getText().toString();
                    if (text.length() > out[0].length()) {
                        out[0] = text;
                    }
                }
            }
        });
        return out[0];
    }

    private View findTabRow(View v) {
        if (v instanceof LinearLayout && ((LinearLayout) v).getOrientation() == LinearLayout.HORIZONTAL
                && ((ViewGroup) v).getChildCount() > 0 && isTabRow((ViewGroup) v)) {
            return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View found = findTabRow(g.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Every child is a fixed-width tab card. */
    private boolean isTabRow(ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) {
            if ("tab-card".equals(g.getChildAt(i).getTag())) {
                return true;
            }
        }
        return false;
    }
}
