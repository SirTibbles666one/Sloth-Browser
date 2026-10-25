#!/usr/bin/env python3
"""Rebuild the shipped 1091 APK as 1092 with a replaced classes4.dex + classes5.dex."""
import sys
import zipfile
from pathlib import Path

SRC = Path(sys.argv[1])
DST = Path(sys.argv[2])
OLD = int(sys.argv[3])
NEW = int(sys.argv[4])
DEX4 = Path(sys.argv[5])
DEX5 = Path(sys.argv[6])


def patch_manifest(data):
    old = OLD.to_bytes(4, "little")
    if data.count(old) != 1:
        raise SystemExit(f"versionCode {OLD} appears {data.count(old)} times")
    data = data.replace(old, NEW.to_bytes(4, "little"))
    u_old, u_new = str(OLD).encode("utf-16-le"), str(NEW).encode("utf-16-le")
    if len(u_old) != len(u_new):
        raise SystemExit("versionName length change")
    if data.count(u_old) != 1:
        raise SystemExit(f"versionName {OLD} appears {data.count(u_old)} times")
    data = data.replace(u_old, u_new)
    if data.count(str(OLD).encode("ascii")) > 1:
        raise SystemExit("ambiguous ascii version")
    return data.replace(str(OLD).encode("ascii"), str(NEW).encode("ascii"))


def main():
    src = zipfile.ZipFile(SRC)
    names = set(src.namelist())
    DST.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(DST, "w") as out:
        for info in src.infolist():
            data = src.read(info.filename)
            if info.filename == "AndroidManifest.xml":
                data = patch_manifest(data)
            elif info.filename == "classes4.dex":
                data = DEX4.read_bytes()
            elif info.filename == "classes5.dex":
                data = DEX5.read_bytes()
            new = zipfile.ZipInfo(info.filename, date_time=info.date_time)
            new.compress_type = info.compress_type
            new.external_attr = info.external_attr
            new.internal_attr = info.internal_attr
            new.create_system = info.create_system
            out.writestr(new, data)
            if info.filename == "classes4.dex" and "classes5.dex" not in names:
                fifth = zipfile.ZipInfo("classes5.dex", date_time=info.date_time)
                fifth.compress_type = zipfile.ZIP_DEFLATED
                fifth.external_attr = info.external_attr
                fifth.create_system = info.create_system
                out.writestr(fifth, DEX5.read_bytes())
    print("wrote", DST, DST.stat().st_size, "bytes")


if __name__ == "__main__":
    main()
