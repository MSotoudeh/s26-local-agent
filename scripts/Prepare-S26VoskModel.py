#!/usr/bin/env python3
"""Prepare one pinned public Vosk archive for S26; no phone/device writes.

Input ZIP is verified against its pinned SHA256. Extraction rejects absolute
paths, traversal, symlinks, filename collisions, archive bombs and oversized
payloads. Output immutable expected-file manifest for Android ModelStore.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import stat
import zipfile

PUBLIC_ID = "asr.vosk.fa-small-05"
FOLDER = "vosk-model-small-fa-0.5"
ZIP_SHA256 = "7e5697239c6bcbfd4188659bb92c8603cd3228a8a4370d789aaeb9d2eeac42c9"
ZIP_SIZE = 62153530
SOURCE = "https://alphacephei.com/vosk/models/vosk-model-small-fa-0.5.zip"
MAX_EXPANDED = 750_000_000
MAX_FILES = 1500


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        while buf := stream.read(2 * 1024 * 1024):
            h.update(buf)
    return h.hexdigest()


def unpack(archive: Path, root: Path, asset: Path) -> dict:
    if archive.stat().st_size != ZIP_SIZE or sha256(archive) != ZIP_SHA256:
        raise ValueError("PUBLIC_ZIP_SIZE_OR_SHA256_MISMATCH")
    if root.exists() or asset.exists():
        raise FileExistsError("OUTPUT_MUST_BE_NEW_AND_NONEXISTING")
    names: set[str] = set()
    members: list[tuple[zipfile.ZipInfo, Path]] = []
    total = 0
    with zipfile.ZipFile(archive) as z:
        if z.testzip() is not None:
            raise ValueError("ZIP_CRC_FAILED")
        for i in z.infolist():
            rel = PurePosixPath(i.filename)
            parts = rel.parts
            if not parts or parts[0] != FOLDER:
                raise ValueError("ARCHIVE_PREFIX_INVALID")
            if len(parts) == 1 and i.is_dir():
                continue
            if len(parts) < 2:
                raise ValueError("ARCHIVE_PREFIX_INVALID")
            if any(t in ("", ".", "..") for t in parts):
                raise ValueError("TRAVERSAL_DETECTED")
            if ":" in i.filename or "\\" in i.filename or i.filename.startswith("/"):
                raise ValueError("INVALID_ZIP_PATH")
            if stat.S_IFMT(i.external_attr >> 16) == stat.S_IFLNK:
                raise ValueError("SYMLINK_NOT_ALLOWED")
            if i.is_dir():
                continue
            relative = Path(*parts[1:])
            name = relative.as_posix()
            if name in names:
                raise ValueError("DUPLICATE_FILE_NAME")
            names.add(name)
            total += i.file_size
            if i.file_size > 500_000_000 or total > MAX_EXPANDED or len(names) > MAX_FILES:
                raise ValueError("EXPANSION_BUDGET_EXCEEDED")
            members.append((i, relative))
        if not {"am/final.mdl", "conf/model.conf"} <= names:
            raise ValueError("MISSING_VOSK_REQUIRED_FILES")
        if root.parent.exists() and shutil.disk_usage(root.parent).free < total + 2_000_000_000:
            raise ValueError("INSUFFICIENT_HOST_STORAGE")
        root.mkdir(parents=True)
        try:
            for info, rel in members:
                dest = root / rel
                dest.parent.mkdir(parents=True, exist_ok=True)
                with z.open(info, "r") as inp, dest.open("xb") as out:
                    shutil.copyfileobj(inp, out, length=1024 * 1024)
                if dest.stat().st_size != info.file_size:
                    raise ValueError("EXTRACTED_FILE_SIZE_MISMATCH")
            filelist = [
                {"path": f.as_posix(), "bytes": (root / f).stat().st_size,
                 "sha256": sha256(root / f)}
                for _, f in sorted(members, key=lambda m: m[1].as_posix())
            ]
            result = {
                "schema_version": "1.0.0", "model_id": PUBLIC_ID,
                "directory": FOLDER, "archive_sha256": ZIP_SHA256,
                "archive_bytes": ZIP_SIZE, "source": SOURCE, "license": "apache-2.0",
                "total_expanded_bytes": total, "files": filelist,
            }
            asset.parent.mkdir(parents=True, exist_ok=True)
            asset.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
            return result
        except BaseException:
            shutil.rmtree(root)
            raise


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--archive", type=Path, required=True)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--asset", type=Path, required=True)
    args = parser.parse_args()
    manifest = unpack(args.archive, args.root, args.asset)
    print(json.dumps({
        "model_id": PUBLIC_ID,
        "verified_archive_sha256": manifest["archive_sha256"],
        "files_verified": len(manifest["files"]),
        "unpacked_bytes": manifest["total_expanded_bytes"],
        "ready_for_non_destructive_adb_push": True
    }, indent=2))


if __name__ == "__main__":
    main()
