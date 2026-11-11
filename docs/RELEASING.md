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

Signing continuity is not optional. From 1252 the certificate is
`b29c6c7f60d31209baf24014963a3630a0c087d0e8d81555a98206b3b2becab6`. A build signed with
anything else cannot install over a 1252 copy.

1252 is a new key. The previous certificate
`3d38d9070b9042b879eb96bbafe949e2dabbf8f6fc798bcc8d60149cd0a947af` is not available, so 1252
does not install over 1251 or anything older. The old app has to be uninstalled first, and
that drops its data. Every later build has to be signed with the 1252 key. The keystore and
its password are not in this repository.

Honesty rules for the note:

* Say what was found, not what was attempted. "The patch never matched the file, so the claim in
  the previous note was false" is the correct sentence when that is what happened.
* If a claim cannot be verified here - anything about how it feels in the hand, anything about
  the system's elevation shadows, anything the harness cannot paint - say that in the note.
