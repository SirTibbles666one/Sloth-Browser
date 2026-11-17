#!/usr/bin/env python3
"""Build classes4.dex: the base navigation dex, routed into the shell.

classes4 in the base APK is the app's own bar and drawer (AddressFace,
DrawerFace, BarUnion). It is not source in this repository. A shipped build
differs from that dex by one call, inserted at the top of AddressFace.paint,
after the null check and inside the existing try:

    invoke-static {p0}, Lcom/sloth/browser/SlothShell;->own(Landroid/app/Activity;)V

That is what makes the shell appear. tools/build.sh used to pass a classes4.dex
it had never written, so the pipeline could not run from the base alone.

If the base dex already contains Lcom/sloth/browser/SlothShell;, it is copied
through unchanged. Otherwise this disassembles it with baksmali 2.5.2, inserts
the call, and assembles it again. Jars are fetched from Maven Central into
the cache and checked against the hashes below. Set SMALI_CP to a classpath of
those jars to skip the fetch.
"""
import hashlib
import os
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

# group, artifact, version, sha256. The set that baksmali/smali 2.5.2 need.
JARS = (
    ("org/smali", "baksmali", "2.5.2",
     "1ed236266d7dc4907aade0b19a34f77efac25342b63c8ace52e579039941b389"),
    ("org/smali", "smali", "2.5.2",
     "136c5c4653d6531bd7b6f10f35f8691cb96432e727d30b4d5579826ee01e9419"),
    ("org/smali", "dexlib2", "2.5.2",
     "5a5c8982d8bd7d6e3bb1a0713049e3c78b719ec32b20f6b619885cec30a0dd61"),
    ("org/smali", "util", "2.5.2",
     "4f580a9cff3ebb83cb3fd20bec88e37e4f183796ca652732992611620282daea"),
    ("com/google/guava", "guava", "27.1-android",
     "686404f2d1d4d221911f96bd627ff60dac2226a5dfa6fb8ba517073eb97ec0ef"),
    ("com/google/guava", "failureaccess", "1.0.1",
     "a171ee4c734dd2da837e4b16be9df4661afab72a41adaf31eb84dfdaf936ca26"),
    ("com/google/code/findbugs", "jsr305", "3.0.2",
     "766ad2a0783f2687962c8ad74ceecc38a28b9f72a2d085ee438b7813e928d0c7"),
    ("com/beust", "jcommander", "1.64",
     "156be736199c990321d9ff77090b199629cfc9865e2d6c13f7cd291bb1641817"),
    ("org/antlr", "antlr", "3.5.2",
     "5ac36c2acfb0a0f3d37dafe20b5b570f2643e2d000c648d44503c2738be643df"),
    ("org/antlr", "antlr-runtime", "3.5.2",
     "ce3fc8ecb10f39e9a3cddcbb2ce350d272d9cd3d0b1e18e6fe73c3b9389c8734"),
    ("org/antlr", "stringtemplate", "3.2.1",
     "f66ce72e965e5301cb0f020e54d2ba6ad76feb91b3cbfc30dbbf00c06a6df6d7"),
    ("antlr", "antlr", "2.7.7",
     "88fbda4b912596b9f56e8e12e580cc954bacfb51776ecfddd3e18fc1cf56dc4c"),
)

ANCHOR = """.method public static paint(Landroid/app/Activity;)V
    .registers 7

    .line 18
    if-nez p0, :cond_3

    return-void

    .line 20
    :cond_3
    :try_start_3
    const-string v0, "binding"
"""

HOOKED = ANCHOR.replace(
    '    :try_start_3\n    const-string v0, "binding"\n',
    '    :try_start_3\n'
    '    invoke-static {p0}, Lcom/sloth/browser/SlothShell;->own(Landroid/app/Activity;)V\n'
    '\n    const-string v0, "binding"\n',
    1,
)

SHELL_TYPE = b"Lcom/sloth/browser/SlothShell;"


def fail(message):
    raise SystemExit(message)


def java_bin():
    home = os.environ.get("JAVA_HOME")
    if home:
        candidate = Path(home) / "bin" / "java"
        if candidate.exists():
            return str(candidate)
    return "java"


def classpath():
    given = os.environ.get("SMALI_CP")
    if given:
        return given
    cache = Path(os.environ.get("SMALI_CACHE", Path.home() / ".cache" / "sloth-smali" / "2.5.2"))
    cache.mkdir(parents=True, exist_ok=True)
    paths = []
    for group, artifact, version, digest in JARS:
        dest = cache / f"{artifact}-{version}.jar"
        if not dest.exists() or hashlib.sha256(dest.read_bytes()).hexdigest() != digest:
            url = (f"https://repo1.maven.org/maven2/{group}/{artifact}/{version}/"
                   f"{artifact}-{version}.jar")
            print(f"fetch {artifact}-{version}")
            try:
                urllib.request.urlretrieve(url, dest)
            except Exception as exc:
                fail(f"could not fetch {url}: {exc}")
            got = hashlib.sha256(dest.read_bytes()).hexdigest()
            if got != digest:
                dest.unlink(missing_ok=True)
                fail(f"{artifact}-{version} sha256 {got} != {digest}")
        paths.append(str(dest))
    return os.pathsep.join(paths)


def run(cmd):
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        sys.stderr.write(result.stdout)
        sys.stderr.write(result.stderr)
        fail("command failed: " + " ".join(cmd[:4]))
    return result


def insert_hook(smali_dir):
    path = smali_dir / "com" / "sloth" / "browser" / "AddressFace.smali"
    if not path.exists():
        fail(f"no AddressFace.smali under {smali_dir}")
    text = path.read_text()
    if "Lcom/sloth/browser/SlothShell;->own(Landroid/app/Activity;)V" in text:
        print("AddressFace.paint already calls SlothShell.own")
        return
    if text.count(ANCHOR) != 1:
        fail(f"AddressFace.paint anchor found {text.count(ANCHOR)} times; "
             "the base dex is not the 1108 navigation dex this hook knows")
    path.write_text(text.replace(ANCHOR, HOOKED, 1))
    print("inserted SlothShell.own into AddressFace.paint")


def main():
    if len(sys.argv) != 3:
        fail("usage: hook_nav.py <base.apk> <classes4.dex>")
    apk, dest = Path(sys.argv[1]), Path(sys.argv[2])
    if not apk.exists():
        fail(f"no such base APK: {apk}")
    with zipfile.ZipFile(apk) as z:
        try:
            blob = z.read("classes4.dex")
        except KeyError:
            fail(f"{apk.name} has no classes4.dex")
    dest.parent.mkdir(parents=True, exist_ok=True)
    if SHELL_TYPE in blob:
        dest.write_bytes(blob)
        print(f"classes4 already references SlothShell; copied {len(blob)} bytes")
        return

    cp = classpath()
    java = java_bin()
    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        raw = work / "classes4.dex"
        raw.write_bytes(blob)
        smali = work / "smali"
        run([java, "-cp", cp, "org.jf.baksmali.Main", "d", str(raw), "-o", str(smali)])
        insert_hook(smali)
        run([java, "-cp", cp, "org.jf.smali.Main", "a", str(smali), "-o", str(dest)])
    out = dest.read_bytes()
    if SHELL_TYPE not in out:
        fail("assembled classes4 does not reference SlothShell")
    print(f"wrote {dest} {len(out)} bytes")


if __name__ == "__main__":
    main()
