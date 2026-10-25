#!/usr/bin/env python3
"""Every name the shell resolves must exist in the shipped app.

The shell reaches into the browser by name: binding fields (urlBar, btnPlus) and
tags on views BarUnion builds (btnBack, slotTabs, pageInfo). A name that does not
exist resolves to null and the control silently does nothing - which is exactly how
every button in 1119-1125 turned out to be dead. This gate reads the names out of
the Java sources and proves each one appears in the app's dex as a field name or a
string constant.

Usage: python3 audit/shell_refs.py <apk> <src-dir> [...]
"""
import re
import sys
import zipfile
from pathlib import Path

# names the shell is allowed to ask for that live in neither place
IGNORED = {"binding", "slothworks"}


def dex_strings(apk):
    blob = b""
    with zipfile.ZipFile(apk) as z:
        for name in z.namelist():
            if name.endswith(".dex"):
                blob += z.read(name)
    return blob


def field_names(blob):
    """Field names appear as string constants or in the string pool; search raw."""
    return blob


def uses(sources):
    # any literal shaped like one of the app's control names, wherever it is passed
    shape = re.compile(
        r'"(btn[A-Za-z0-9_]+|slot[A-Za-z0-9_]+|urlBar|pageInfo|tabCount|omniLock|'
        r'panel[A-Za-z0-9_]*|chrome[A-Za-z0-9_]+|settings|geckoView)"')
    names = set()
    for path in sources:
        for match in shape.finditer(Path(path).read_text()):
            names.add(match.group(1))
    return names


def reflection_targets(sources):
    """Class.forName(..).getMethod(..) / getField(..) pairs the shell relies on."""
    targets = []
    pattern = re.compile(
        r'Class\.forName\("([A-Za-z0-9_.]+)"\)\s*\.\s*(getMethod|getField|getDeclaredMethod|getDeclaredField)\("([A-Za-z0-9_]+)"', re.S)
    for path in sources:
        for match in pattern.finditer(Path(path).read_text()):
            targets.append((match.group(1), match.group(2), match.group(3)))
    return targets


def main():
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    apk = sys.argv[1]
    sources = []
    for arg in sys.argv[2:]:
        p = Path(arg)
        sources.extend(sorted(p.rglob("*.java")) if p.is_dir() else [p])

    blob = dex_strings(apk)
    names = sorted(uses(sources) - IGNORED)

    missing = []
    for name in names:
        if name.encode() not in blob:
            missing.append(name)

    print(f"{len(names)} names referenced by the shell")
    for name in names:
        print(f"  {'MISSING' if name in missing else 'ok     '} {name}")
    if missing:
        print(f"\n{len(missing)} name(s) do not exist in {Path(apk).name}: {', '.join(missing)}")
        raise SystemExit(1)
    targets = reflection_targets(sources)
    print(f"\n{len(targets)} reflected member(s)")
    bad = []
    for cls, kind, name in sorted(set(targets)):
        simple = cls.rsplit(".", 1)[-1]
        class_ok = simple.encode() in blob
        member_ok = name.encode() in blob
        status = "ok" if (class_ok and member_ok) else "MISSING"
        if status == "MISSING":
            bad.append(f"{cls}.{name}")
        print(f"  {status:7} {cls}.{name} ({kind})")
    if bad:
        print(f"\n{len(bad)} reflected member(s) not found: {', '.join(bad)}")
        raise SystemExit(1)

    check_signatures()

    print("\nEvery referenced name exists in the app.")


def check_signatures():
    """Exact signatures, checked against the base app's smali when it is around."""
    manifest = Path(__file__).with_name("reflection.txt")
    smali = Path(__file__).with_name("smali")
    if not manifest.exists():
        return
    lines = [ln.strip() for ln in manifest.read_text().splitlines()
             if ln.strip() and not ln.strip().startswith("#")]
    print(f"\n{len(lines)} reflected signature(s)")
    missing = []
    for line in lines:
        cls, sig = line.split("#", 1)
        simple = cls.rsplit(".", 1)[-1]
        path = smali / (cls.replace(".", "/") + ".smali")
        if not path.exists():
            print(f"  SKIP    {cls} (no smali checked out)")
            continue
        ok = sig in path.read_text()
        if not ok:
            missing.append(f"{cls}#{sig}")
        print(f"  {'ok' if ok else 'MISSING':7} {simple}#{sig}")
    if missing:
        print(f"\n{len(missing)} reflected signature(s) do not exist: {', '.join(missing)}")
        raise SystemExit(1)


if __name__ == "__main__":
    main()
