# Sloth Browser — the shell

The part of Sloth Browser that is ours.

The app itself is closed-source: a signed APK is the only thing ever published, in
[Sloth-Browser-releases](https://github.com/SirTibbles666one/Sloth-Browser-releases). What lives
here is the layer we build on top of it — the bar, the sheet, the settings page, the diagnostics,
the icons, and the tests that keep them honest — plus the scripts that turn them back into an
installable APK and the checks that refuse to ship when something is missing.

Nothing in this repository can be assembled into a runnable browser on its own. It is a patch
layer, and it says so.

## How the thing is built

The shipped APK is a repackaged build of the app's own release:

1. `src/main/java/com/sloth/browser/` is compiled against the Android SDK and the compile-only
   stubs in `stubs/`, producing one dex.
2. `src/patches/` holds the older patch classes that are still part of the shipped binary
   (the drawer's resting maths, the plate, the ui helpers).
3. Those dexes are merged into a **base APK** that is not in this repository — the app's own
   build, which we are not entitled to redistribute.
4. `tools/build_apk2.py` bumps the manifest, replaces the dexes, strips the old signature block
   and hands the result to `zipalign` and `apksigner`.
5. `audit/shell_refs.py` proves every name the shell reflects on actually exists in the app;
   `audit/ship_gate.py` proves the APK carries the shell and both gates pass — this is what makes
   a build shippable.

The signing key is not in this repository either. A build needs the app's keystore to install over
an existing copy; without it you can still compile and test, and you cannot produce an update.

## The shape of the shell

One surface carries the bar and the drawer. The bar is the handle — a vertical drag anywhere on it
grows it into the sheet, with four resting sizes (shut, the 72 dp tab strip, the fitted content,
the full sheet). A sideways flick across the bar changes tab. The strip holds close, one square
per open tab, and a plus; holding a square closes that tab.

The sheet's content is a dashboard built in code: themed cards, a calculator, notes, a clock, the
mirrored tab row, a widget chooser, live counters. Under it all, a layered painted forest that
moves at different depths as the sheet comes out.

Settings, diagnostics and the back stack are shell pages drawn over the app, because the app's own
settings UI is a different thing wearing the same name.

## Rules this code has already learned the hard way

Written down because each one cost a shipped build:

- **The surface gets an explicit height.** A wrapping `FrameLayout` measures a `MATCH_PARENT`
  backdrop against the whole window, and the forest lands on top of the page.
- **The page position is measured, never guessed.** Adding a hardcoded bar height on top of
  padding that already included the status bar put the page one status bar too low.
- **A view with no session is a black rectangle.** Diagnostics separates "covered", "empty 0×0"
  and "no session", because those look identical in a photograph.
- **Animations carry a generation.** A close bumps it, so frames belonging to the old state stop
  instead of undoing the close.
- **Reflection walks the class chain.** `getDeclaredField` misses inherited fields; writes must
  dispatch on the real type.
- **A reveal is frame-counted.** A `ViewPropertyAnimator` with alpha pre-set to zero never ran,
  and left the dashboard invisible.
- **Drawn controls, not glyphs.** A text glyph carries its own bearings; five of them never line
  up into a row.
- **The strip has two thresholds.** One threshold meant a slow drag flipped between strip and
  dashboard every few pixels, which read as the drawer fighting the finger.
- **A drag translates the page, it does not relayout it.** Padding the page root on every move
  resized the engine surface on every move; translation is a compositor shift, and the real
  offset is committed once on release.
- **Moves are coalesced, never dropped.** The newest sample is applied once per loop turn: two
  moves for one frame must not become two layout passes, and must not become lag either.
- **A drag takes the travel it spent claiming the gesture**, or the sheet lags the thumb by the
  touch slop and then lurches.
- **Nothing is claimed in a release note without a test.** One build said the sheet cast a shadow
  when the patch adding the elevation had silently not applied.

## Tests

`tests/` is a Robolectric harness that renders the shell on the JVM in native graphics mode and
asserts behaviour rather than pixels: 161 tests at the last build, including the drag, the peek
hysteresis, the settings bindings, the diagnostics report, the back order and the drawn icons.

```sh
cd tests
JAVA_HOME=<jdk17> ANDROID_HOME=<sdk> gradle testDebugUnitTest
```

`ConceptRenderTest`-style rendering lives here too: a handful of the tests write PNGs of each state
to a scratch directory so a change can be looked at before it is installed.

## State

The shell patches the published **1108** base (`c6cec08107d57ffc9b566051accb54b7df814ffd1ec616a3a59c0a6f2850089e`).
Signed builds from 1252 on use the key recorded in `docs/RELEASING.md`, and they do not install
over anything signed before that. 1252 and 1253 are the releases currently published. 1254 is
the load, address, and first-run work on top of that, not yet published.

What the shell now does with the page, and where it lives so a later chrome can take it:

- `SlothLoad` watches page start, progress, and stop, and keeps the tab's real URL.
- `SlothAddress` turns that URL into the host the bar shows, and back into the full URL on tap.
  The lock uses the same URL: `http` is insecure, and a host with the scheme already stripped is not.
- `SlothFirstRun` notices the welcome screen so the shell stays out of its way.
  Once the app's `onboardingDone` flag is set, that walk stops.
- Block screenshots follows the setting, including the window flag.
- The tab list is the view tagged `tab-squares`, not the bar's tab button.
- Settings exports and restores the encrypted vault, and says so. Restore asks first,
  because it replaces bookmarks, history and logins. With no passphrase there is no file,
  and the export says that instead of pretending it started.

Open, honestly:

- Bookmarks, History, and Downloads still depend on the app drawing into a lent panel.
- `SlothShell` is still the chrome. The bar and drawer are due to be replaced behind a switch;
  the three classes above are the pieces meant to move with that.
- `docs/AUDIT.md` is the 1223 record. Several of its open items (edge-to-edge, loading progress)
  have since been done; the rest of that list is still the list.
