# Audit — build 1223

A read of the whole shell: 15 classes, 7,254 lines at the start of the pass. What follows is
what was wrong, in the order it matters, and what was done about it. Fixed means a test or a
gate fails without the change; open means it is still open and why.

## Fixed in 1223

### 1. The drawer closed itself while you scrolled it — HIGH
`SlothSheetScroll.onTouchEvent` called `listener.onRelease(...)` on `ACTION_UP` whether or not
it had claimed the gesture. Every scroll of the dashboard therefore ended in a release, the
shell measured the finger's speed as if it had dragged the sheet, and `finishDrag` threw the
drawer to the next seat in that direction: scroll up fast and the drawer shut.

- The list now reports a release only when it owns the drag.
- The shell's `onRelease` ignores a release when `dragging` is false, so no child can settle a
  sheet it did not claim.
- Test `scrollingTheListDoesNotReleaseTheSheet` — verified failing on 1222's code.

### 2. The strip could only be pulled from the gaps — HIGH
The peek strip's vertical drag was attached to the `HorizontalScrollView`. A touch that lands
on a tab square is delivered to the square, so the container's listener never saw it: vertical
drag worked on the gaps and nowhere else, which matches "the tab drawer part is still buggy".

- The drag instance is kept and attached to every child as well, after the squares are built.
- Test `theStripDragCoversEverySquare` dispatches a real touch through a square and asserts the
  sheet moved.

### 3. A refused settings write left the switch flipped — MEDIUM
`toggle()` painted the new position before checking whether `SlothPrefs.write` succeeded. A
refused write left the row claiming a setting the browser never took. It now stays where it
was and logs `ERROR could not save <field>`.

### 4. A layer check ran once per frame at zero height — MEDIUM (performance)
`setDrawerHeight(0)` called `checkLayers`, which walks the window and reads the log. A drag
held shut called it every frame. It now runs on the transition from >0 to 0 only.

### 5. Status bar height came from a deprecated call — MEDIUM (Android 15+)
`statusBarHeight()` read `getSystemWindowInsetTop()` first. Under the edge-to-edge window that
Android 15 enforces for apps targeting 35+, the typed inset is the one that stays correct.
The typed inset is used on API 30+, the legacy getter only below it.

### 6. The log committed to storage for every line — MEDIUM (performance)
`SlothLog.note()` did a synchronous `commit()` per line on the main thread; the probe and the
tick can emit several a second. Notes use `apply()`; `error()` and the crash path still commit,
because those are the lines whose loss would leave a report empty.

### 7. Velocity was measured from the first touch, not from the claim — LOW
`SlothSheetScroll` and `SlothDrag` reset the position at claim but not the time and height the
speed is derived from, so the first move after a claim could read as a spike. Both now set
`lastY`/`lastT` at the claim point, and a cancelled gesture reports velocity 0 instead of
whatever the last move implied.

### 8. Duplicate and silent blocks — LOW
- `backOneLayer` had the loan-release block written twice; the second copy could never run.
- `refresh` had two copies of "close a drawer nothing asked for", and swallowed every throwable
  silently. One copy remains, and the failure is logged.

### 9. The ship gate inferred class names from file paths — LOW, but it lied
`audit/ship_gate.py` built a type descriptor from the path between `/src/` and the file name.
Moving the sources into this repository (`src/main/java/...`) made it report all 18 classes as
missing — a gate that fails on a refactor and passes on a broken build is worse than none. It
reads the `package` declaration now.

## Verified, not changed

- `SlothPrefs` walks the class chain and dispatches writes on the real field type; the
  once-per-field reporting is correct.
- `SlothCover` judges drawn position (translation), skips views that contain the page, skips the
  shell's own surface, and reports alpha and background — the five false "cover" lines in the
  1218-era log cannot recur from layout position.
- `SlothBar` declines `requestDisallowInterceptTouchEvent` on purpose: the address field must
  not become a dead zone. Interception is still gated on a vertical drag, so the field keeps its
  taps and sideways swipes.
- The 800 ms tick guards its own loop list, so a replaced shell cannot leave a loop running, and
  `releaseShell` clears every static including the strip's drag.
- No `TODO`, `FIXME` or `XXX` anywhere; no private method is defined without a caller.

## 1226-1228: the unification, and three gesture faults

The bar and the dashboard were merged into one surface in 1226, and instrumenting the drag in
1227 found three faults in 1228 that no amount of tuning would have fixed.

**A strip drag reported a delta where the shell expected a total (1228).** `SlothDrag` handed the
shell `event.getY() - lastY` per move, and the shell positions the sheet at `startH + dy`. So every
sample recomputed the height from the same origin: the sheet moved by one step and then stopped,
however far the finger travelled. The fix is an accumulating `total` in `SlothDrag`; the test asserts
the contract (three steps of 50 report 100 after the claim), not the arithmetic inside the shell.

**A release could discard the last sample (1228).** Moves are coalesced to one per loop turn. A
finger lifting between two turns left `dragPending` unapplied, and the seat was then chosen from a
height the finger had already left. `onRelease` now applies the pending sample first.

**The status bar strip belonged to the platform (1228).** Android 15 paints it edge to edge, which
left a band of a different colour above a panel that is ours. It is now a `status-scrim` view in the
panel's colour, with the system glyph appearance set from that colour's luminance, and it is
repainted to the measured inset (the view is built before the insets are known). This closes the
`setStatusBarColor` item below for our panel: the colour is drawn by us rather than requested.

**The same class of bug is now checked across all three drag sources.** `SlothBar` (intercept and
touch paths), `SlothSheetScroll` and `SlothDrag` all report travel since the claim, and all three
reset their origin at the claim; the two that carry pre-claim travel pass it once, as `carry`.

**Removed with the unification:** the bar's own capsule (a second rounded rect inside the panel),
the drawer's fade band, the 20dp grabber band, the divider under the bar, the drawn hinge and the
now-dead `SlothEdge`.

## 1232: what four parallel audits found, and what was fixed

The audits were run by separate readers over the shell, the dashboard, the settings/back/diagnostics
layer, and the build plumbing. Only the findings that were re-checked against the code here are listed.

**Fixed, in this build**

- **The edge-to-edge fallback undid itself.** `checkEdgeWindow` restored a fitted window and then
  called `rebuild()`, which called `requestEdgeToEdge()` again and asked for edge-to-edge a second
  time - one frame after giving it up - while `edgeChecked` stopped it being judged again. The
  request now latches on `edgeFellBack`.
- **The self-check could never fire.** It compared `surfaceView.getTop()` (always 0: a Gravity.TOP
  child) plus a padding that is by construction larger than the strip. It now judges the panel's
  screen position, which catches both ways this can be wrong: content under the strip, or a panel
  pushed down by a window that still fits its content.
- **The page offset was fed its own translation.** `getLocationOnScreen` walks the hierarchy
  applying each view's matrix, so `rootAt[1]` already contained the `translationY` the method was
  about to set. The live branch was self-referential: the page converged on half the travel, and the
  commit left it short. The translation is now undone once before the arithmetic.
- **A release flushed the same pending sample twice**, the second time into a condition already
  false; the duplicate is gone.
- **`releaseShell` kept the page.** `appRoot` and its paddings survived detach (a GeckoView held
  alive, and the next window inheriting the last one's insets), alongside `statusInset`, `navSeen`
  and `edgeChecked`. All cleared, and the generation bumped so posted frames stop.
- **The strip grew a nav band.** `applyBottomInset` wrote the nav padding unconditionally, putting a
  nav-height stripe of empty paper inside the 72dp strip on any later inset dispatch. It respects
  the peek face now.
- **Drawn bar controls were removed from the accessibility tree.** `SlothIcons` set
  `IMPORTANT_FOR_ACCESSIBILITY_NO` for every icon, so the content descriptions the bar sets were
  never spoken. Only `decorative()` opts out now.
- **The note counter never moved**: it was written once at build, so a full note still read
  "empty".
- **Every refresh rebuilt the tab row**: `stale()` compared the card's `readableTitle()` against the
  raw label, which never matched for an untitled tab ("Tab 1" vs "Tabs, 1").
- **A stepper painted a value it could not save**, where the toggles beside it revert.

**Fixed in the gates, because a green run proved nothing**

- `ship_gate.py` reported `0/0 source classes` and `Ship gate passed` on a zip of two blobs, and
  skipped the certificate and v2/v3 checks entirely because `tools/android-16/apksigner` does not
  exist. It now fails on an empty scan, resolves `aapt2`/`apksigner` from the SDK, reads versionCode
  as an integer attribute instead of grepping for its digits, and rejects a truncated zip. Verified
  failing: wrong version, empty source dir.
- `shell_refs.py` looked for the shell's own string literals in the shell's own dex, so it could
  never fail. It now refuses a built APK (exit 2) and refuses an empty scan.
- CI: a test-count floor (`tests/min-tests.txt`) after the harness, and the report artifact is an
  error when missing.

**Left open, with reasons**

- `SlothSettings` handles BACK in an `OnKeyListener` on the page, so the tested `SlothBack` branch
  is not the one that runs on a phone (a press rebuilds the shell instead of hiding one layer).
  Fixing it means routing the page's key to `backOneLayer` and re-testing the whole order; it is the
  next build.
- `SlothCover` applies `translationY` twice (its rect already includes it), so a half-open panel is
  reported at about half its real coverage. The discriminating test needs a laid-out activity the
  harness cannot provide yet.
- Two toggles read with a hardcoded `false` fallback while the model's defaults are `true`, so a
  failed read shows "off" for six rows. Needs a per-row default table.
- Weighted board children measure 0 high inside a wrap-content parent, and hiding the calculator
  leaves its keypad behind. Both need the widget registry rather than one-line fixes.

## Open, with reasons

- **The status bar strip is painted by us, but the window is not edge-to-edge.** Our scrim covers
  the measured inset in the panel's colour, which is what the eye sees; a true edge-to-edge window
  (decor fit off, page drawn under the bars) would be the larger rework, and is not done.
- **No on-device verification.** Nothing here proves how the drag *feels*, only what the events
  and arithmetic do. The phone is the only renderer.
- **`SlothShell` is 3,500 lines with static state.** The class is reachable only through
  statics; splitting it is a refactor with real risk and no user-visible gain this pass.
- **`refresh()` still runs two view-tree walks** (chrome list and panel list) every time the
  shell is re-owned. Cached, but not free; worth measuring on device before changing.
- **Fifty `catch` blocks swallow exceptions.** The two on the paths that matter now log. The
  rest are in places where a failure means "this optional decoration did not draw", and a log
  line there would be noise; that judgment would need a real crash to overturn.
