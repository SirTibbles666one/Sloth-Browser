#!/usr/bin/env python3
"""Post-build gate: the shipped APK must contain exactly what the sources say.

Every check here exists because it once passed wrongly. The first version of this gate printed
"source classes in dex: 0/0", skipped the signing checks entirely because the apksigner path it
looked for (tools/android-16) was never populated, and accepted a versionCode it found as a
substring - so a green run proved nothing, on a zip of two blobs.

The rules now:
  - an empty scan is a failure, not a pass;
  - a verifier that cannot be found is a failure, not a skip;
  - the version is read as an integer attribute, because "9.1230" and a manifest with no
    versionCode at all both used to pass;
  - a truncated zip is a failure.

Usage: python3 audit/ship_gate.py <apk> <expected-versionCode> <src-dir> [more-src-dirs...]
"""
import hashlib
import os
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

CERT_SHA256 = "3d38d9070b9042b879eb96bbafe949e2dabbf8f6fc798bcc8d60149cd0a947af"
# the shell is 14 classes; a scan that finds far fewer is looking in the wrong place
MIN_SOURCE_CLASSES = 14


def fail(message):
    print(f"  FAIL    {message}")
    return 1


def sdk_tool(name):
    """Find a build-tool in the Android SDK, wherever this machine keeps it."""
    candidates = []
    for root in (os.environ.get("ANDROID_SDK_ROOT"), os.environ.get("ANDROID_HOME")):
        if root:
            candidates.extend(sorted(Path(root).glob(f"build-tools/*/{name}"), reverse=True))
    found = shutil.which(name)
    if found:
        candidates.append(Path(found))
    for path in candidates:
        if path.exists():
            return path
    return None


def version_of(apk):
    """The versionCode and versionName as the package actually declares them."""
    aapt2 = sdk_tool("aapt2")
    if aapt2 is None:
        return None, None, "aapt2 not found, so the version cannot be read"
    out = subprocess.run([str(aapt2), "dump", "badging", str(apk)],
                         capture_output=True, text=True).stdout
    code = re.search(r"versionCode='(\d+)'", out)
    name = re.search(r"versionName='([^']*)'", out)
    if code is None:
        return None, None, "the package declares no versionCode"
    return code.group(1), (name.group(1) if name else ""), None


def main():
    if len(sys.argv) < 4:
        raise SystemExit(__doc__)
    apk, expected = Path(sys.argv[1]), sys.argv[2]
    srcs = [Path(a) for a in sys.argv[3:]]
    problems = 0

    if not apk.exists():
        raise SystemExit(f"no such APK: {apk}")

    with zipfile.ZipFile(apk) as z:
        broken = z.testzip()
        names = z.namelist()
        dexs = [n for n in names if n.endswith(".dex")]
        manifest = z.read("AndroidManifest.xml") if "AndroidManifest.xml" in names else b""
        blob = b"".join(z.read(n) for n in dexs) if dexs else b""

    if broken:
        problems += fail(f"the zip is truncated at {broken}")

    print(f"package: {'ok' if manifest else 'missing AndroidManifest'}")
    if not manifest:
        problems += 1

    print(f"dex files: {len(dexs)} {'ok' if len(dexs) >= 2 else 'FAIL expected 2+'}")
    if len(dexs) < 2:
        problems += 1

    # the version, read as a value rather than searched for as text
    got, got_name, why = version_of(apk)
    if why:
        problems += fail(why)
    else:
        ok = got == str(expected)
        print(f"versionCode {expected}: {'ok' if ok else 'FAIL found ' + got}")
        if not ok:
            problems += 1
        print(f"versionName: {got_name or '(unnamed build)'}")

    # each source class must exist as a type descriptor in some dex
    sources = [p for src in srcs if src.exists() for p in sorted(src.rglob("*.java"))]
    if not sources:
        problems += fail(f"no sources found under {[str(s) for s in srcs]}")
    elif len(sources) < MIN_SOURCE_CLASSES:
        problems += fail(f"only {len(sources)} source files found: an empty or partial scan"
                         f" must not pass")
    missing = []
    for path in sources:
        # the descriptor comes from the package the file declares, not from where it sits: this
        # gate used to infer it from the path, and moving the sources made every class "missing"
        text = path.read_text(errors="replace")
        pkg = re.search(r"^\s*package\s+([\w.]+)\s*;", text, re.M)
        desc = "L" + (pkg.group(1).replace(".", "/") + "/" if pkg else "") + path.stem + ";"
        if desc.encode() not in blob:
            missing.append(path.name)
    print(f"source classes in dex: {len(sources) - len(missing)}/{len(sources)}")
    for name in missing:
        problems += fail(f"{name} is not in the dex")

    # signing: the certificate and both install-carrying schemes
    verifier = sdk_tool("apksigner")
    if verifier is None:
        problems += fail("apksigner not found: the certificate and v2/v3 are unverified")
    else:
        out = subprocess.run([str(verifier), "verify", "--print-certs", str(apk)],
                             capture_output=True, text=True).stdout
        schemes = subprocess.run([str(verifier), "verify", "--verbose", str(apk)],
                                 capture_output=True, text=True).stdout
        cert = next((ln.split(":")[-1].strip() for ln in out.splitlines()
                     if "SHA-256 digest" in ln), "none")
        ok = cert.startswith(CERT_SHA256)
        print(f"certificate: {'ok ' + cert[:16] if ok else 'FAIL ' + cert[:40]}")
        if not ok:
            problems += 1
        # minSdk 26 means v2/v3 carry install; apksigner reports v1 as false even though it
        # writes the JAR signature files, so only v2 and v3 are required
        for scheme in ("v2", "v3"):
            present = re.search(rf"Verified using {scheme} scheme.*true", schemes) is not None
            print(f"{scheme} signature: {'ok' if present else 'FAIL'}")
            if not present:
                problems += 1

    print(f"\nsha256 {hashlib.sha256(apk.read_bytes()).hexdigest()}")
    print(f"size   {apk.stat().st_size}")
    if problems:
        print(f"\n{problems} problem(s) - do not publish")
        raise SystemExit(1)
    print("\nShip gate passed.")


if __name__ == "__main__":
    main()
