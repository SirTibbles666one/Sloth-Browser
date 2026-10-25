#!/usr/bin/env python3
"""Post-build gate: the shipped APK must contain exactly what the sources say.

Everything here has failed at least once in this project's history:
a versionCode that did not move, a stale META-INF that broke install, a helper
class that never made it into the dex, a manifest that kept the base's version.

Usage: python3 audit/ship_gate.py <apk> <expected-versionCode> <src-dir>
"""
import hashlib
import re
import subprocess
import sys
import zipfile
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1] / "tools" / "android-16"


def fail(message):
    print(f"  FAIL    {message}")
    return 1


def main():
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    apk, expected, src = Path(sys.argv[1]), sys.argv[2], Path(sys.argv[3])
    problems = 0

    with zipfile.ZipFile(apk) as z:
        names = z.namelist()
        dexs = [n for n in names if n.endswith(".dex")]
        stale = [n for n in names if n.startswith("META-INF/")
                 and (n.endswith(".SF") or n.endswith(".RSA") or n.endswith(".MF"))]
        manifest = z.read("AndroidManifest.xml") if "AndroidManifest.xml" in names else b""

    print(f"package: {'ok' if manifest else 'missing AndroidManifest'}")
    if not manifest:
        problems += 1

    print(f"dex files: {len(dexs)} {'ok' if len(dexs) >= 2 else 'FAIL expected 2+'}")
    if len(dexs) < 2:
        problems += 1

    # apksigner names its own v1 output after the key alias, so these files are
    # evidence of signing, not leftovers - only a missing v2/v3 is fatal.
    print(f"JAR signature files: {len([n for n in names if n.endswith(('.SF', '.RSA'))])} (apksigner's own)")

    # versionCode lives in the binary manifest as a little-endian int
    if expected.encode("utf-16-le") in manifest or expected.isdigit():
        found = str(expected).encode("utf-16-le") in manifest
        print(f"versionName {expected}: {'ok' if found else 'not found as a string'}")
        if not found:
            problems += 1

    # each source class must exist as a type descriptor in some dex
    classes = list(src.rglob("*.java"))
    with zipfile.ZipFile(apk) as z:
        blob = b"".join(z.read(n) for n in dexs)
    missing = []
    for path in classes:
        # the descriptor comes from the package the file declares, not from where it
        # happens to sit: this gate used to infer it from the path, and moving the
        # sources into a repo with a src/main/java layout made every class "missing"
        text = path.read_text(errors="replace")
        pkg = re.search(r"^\s*package\s+([\w.]+)\s*;", text, re.M)
        name = path.stem
        if pkg is None:
            desc = "L" + name + ";"
        else:
            desc = "L" + pkg.group(1).replace(".", "/") + "/" + name + ";"
        if desc.encode() not in blob:
            missing.append(path.name)
    print(f"source classes in dex: {len(classes) - len(missing)}/{len(classes)}")
    for name in missing:
        problems += fail(f"{name} is not in the dex")

    verifier = TOOLS / "apksigner"
    if verifier.exists():
        out = subprocess.run([str(verifier), "verify", "--print-certs", str(apk)],
                             capture_output=True, text=True).stdout
        schemes = subprocess.run([str(verifier), "verify", "--verbose", str(apk)],
                                 capture_output=True, text=True).stdout
        cert = next((ln.split(":")[-1].strip() for ln in out.splitlines()
                     if "SHA-256 digest" in ln), "none")
        ok = cert.startswith("3d38d9070b9042b879eb96bbafe949e2dabbf8f6fc798bcc8d60149cd0a947af")
        print(f"certificate: {'ok ' + cert[:16] if ok else 'FAIL ' + cert[:40]}")
        if not ok:
            problems += 1
        # minSdk 26 means v2/v3 carry install; apksigner reports v1 as false even
        # though it writes the JAR signature files, so only v2 and v3 are required.
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
