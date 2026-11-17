# Releasing

A build is shippable when all three gates pass, and not before.

1. `tools/build.sh <versionCode>` compiles, dexes, repackages, aligns, signs and then runs both
   audits. It prints the sha256; that number goes in the release note.
2. `tests/` must pass. A behaviour claimed in a release note needs a test in this harness that
   would fail without the change.
3. Publish to `Sloth-Browser-releases` as a **pre-release** with the APK and a `version.json`
   (`{"versionName":"<n>","versionCode":<n>,"apk":"SlothBrowser-<n>.apk"}`), then promote it so
   `/releases/latest` serves it — the in-app updater reads that endpoint.
4. Verify after promoting: `curl -s -o /dev/null -w '%{redirect_url}' .../releases/latest` must
   name the new tag, and the `version.json` on that tag must name the new build.

Signing continuity is not optional. The release certificate is
`3d38d9070b9042b879eb96bbafe949e2dabbf8f6fc798bcc8d60149cd0a947af`, the owner's key, alias
`sloth`. It signed every build up to 1251. A build signed with anything else cannot install
over those copies.

History, so nobody repeats it:

| Builds | Signed with | Installs over |
| --- | --- | --- |
| up to 1251 | 3d38... (release key) | each other |
| 1252, 1253 | b29c6c7f... (a pipeline without the release key) | only each other: 1251 had to be uninstalled |
| 1254 | ccbd3cd0... (a throwaway test key) | nothing: installed after an uninstall |
| 1255 | 3d38..., with a rotation record from ccbd3cd0... | 1254, and anything on 3d38 running Android 9+ |
| 1256 on | 3d38... alone | 1255 and everything on 3d38 |

A rotation record (`docs/signing/*.lineage`) lets a phone holding a build signed with an older
key accept one signed with a newer key, without an uninstall. It needs both keys to make, and
`tools/build.sh` uses it when `LINEAGE` and `OLD_KEYSTORE` are set. A rotation build's v1/v2
blocks are signed with the old key, so it is for the phones on that key only; public releases
are built without `LINEAGE`. Phones on 1252 or 1253 need the b29c key to get a record; without
it they must export the vault and reinstall.

Honesty rules for the note:

* Say what was found, not what was attempted. "The patch never matched the file, so the claim in
  the previous note was false" is the correct sentence when that is what happened.
* If a claim cannot be verified here - anything about how it feels in the hand, anything about
  the system's elevation shadows, anything the harness cannot paint - say that in the note.

## A new base

The base is the app's own build before any shell: the published 1108 release, pinned by
sha256 in `tools/build.sh` and in the `reflective names` CI job. A newer engine means a new
base, and it is a deliberate change:

1. Record the new base's sha256 and its GeckoView version here.
2. Change the pin in `tools/build.sh` and `.github/workflows/gates.yml` in the same PR.
3. The `reflective names` job must pass against it: every name the shell reaches for by
   reflection has to exist in the new app.
4. Build, run the ship gate, and check the release on a phone before publishing.
