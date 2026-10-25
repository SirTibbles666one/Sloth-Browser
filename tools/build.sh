#!/usr/bin/env bash
# Build a Sloth Browser APK from this shell plus a base APK you supply.
#
# What is not here, on purpose:
#   * the base APK (the app's own build) - we are not entitled to redistribute it
#   * the signing key - without it the result cannot install over an existing copy
#
# Usage:
#   BASE_APK=... KEYSTORE=... KS_PASS=... KEY_PASS=... ./tools/build.sh 1223
#
# The dex split matters: classes4 is the app's navigation hook (DrawerFace painting routed
# into SlothShell.own), classes5 is everything in src/main/java. Both are built here; the
# old patch classes ride along in the same dex as the shell.
set -euo pipefail

VERSION="${1:?usage: build.sh <versionCode>}"
: "${BASE_APK:?set BASE_APK to the app's own release apk}"
: "${KEYSTORE:?set KEYSTORE to the signing keystore}"
: "${KS_PASS:?set KS_PASS}"
: "${KEY_PASS:?set KEY_PASS}"
: "${ANDROID_HOME:?set ANDROID_HOME}"
: "${JAVA_HOME:?set JAVA_HOME}"

HERE="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${OUT:-$HERE/build}"
BUILD_TOOLS="${BUILD_TOOLS:-$ANDROID_HOME/build-tools/36.0.0}"
API="${API:-36}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

mkdir -p "$OUT"

echo "== compile the shell against the stubs"
javac -source 8 -target 8 -classpath "$ANDROID_HOME/platforms/android-$API/android.jar" \
    -sourcepath "$HERE/stubs" -d "$WORK/classes" \
    "$HERE/src/patches/com/sloth/browser/"*.java \
    "$HERE/src/main/java/com/sloth/browser/"*.java

echo "== dex it"
"$BUILD_TOOLS/d8" --release --min-api 26 --lib "$ANDROID_HOME/platforms/android-$API/android.jar" \
    --output "$WORK/dex" "$WORK/classes/com/sloth/browser/"*.class

echo "== unpack the base and swap the dexes"
python3 "$HERE/tools/build_apk2.py" "$BASE_APK" "$WORK/unsigned.apk" 1108 "$VERSION" \
    "$WORK/classes4.dex" "$WORK/dex/classes.dex"

echo "== drop the base signature block"
python3 - "$WORK/unsigned.apk" "$WORK/clean.apk" <<'PY'
import sys, zipfile
src, dst = sys.argv[1], sys.argv[2]
drop = {"META-INF/SLOTH.SF", "META-INF/SLOTH.RSA", "META-INF/MANIFEST.MF"}
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w") as zout:
    for item in zin.infolist():
        if item.filename in drop:
            continue
        clone = zipfile.ZipInfo(item.filename, date_time=item.date_time)
        clone.compress_type = item.compress_type
        clone.external_attr = item.external_attr
        clone.internal_attr = item.internal_attr
        zout.writestr(clone, zin.read(item.filename))
PY

echo "== align and sign"
"$BUILD_TOOLS/zipalign" -p -f 4 "$WORK/clean.apk" "$WORK/aligned.apk"
"$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-key-alias sloth \
    --ks-pass "pass:$KS_PASS" --key-pass "pass:$KEY_PASS" \
    --min-sdk-version 26 --v1-signing-enabled true --v2-signing-enabled true \
    --v3-signing-enabled true --out "$OUT/SlothBrowser-$VERSION.apk" "$WORK/aligned.apk"

echo "== gates"
python3 "$HERE/audit/shell_refs.py" "$OUT/SlothBrowser-$VERSION.apk" "$HERE/src/main/java/com/sloth/browser"
python3 "$HERE/audit/ship_gate.py" "$OUT/SlothBrowser-$VERSION.apk" "$VERSION" "$HERE/src"

sha256sum "$OUT/SlothBrowser-$VERSION.apk"
