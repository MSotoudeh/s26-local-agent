#!/usr/bin/env python3
"""Verify the official Apache-2.0 OmniASR CTC 300M Int8 archive and
extract strictly allowlisted files for S26 local ASR. No phone access.
This program refuses output overwrite and does not delete anything.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import tarfile

MODEL_DIR = "sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-int8-2025-11-12"
PUBLIC_ID = "asr.omni_ctc_300m.int8"
SOURCE = ("https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/"
          + MODEL_DIR + ".tar.bz2")
EXPECTED_SHA = "cdcd0559c7c73efed54209a926e321afc914d046c5fdbf3665f00dc78180e5ed"
EXPECTED_BYTES = 292571207
EXPECTED_FILES = {"model.int8.onnx", "tokens.txt", "LICENSE"}
MAX_FILE = 500_000_000
MAX_UNPACK = 600_000_000

def sha(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(2 * 1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()

def prepare(archive, out, manifest):
    if out.exists() or manifest.exists():
        raise ValueError("OUTPUT_COLLISION_REQUIRES_REVIEW")
    if archive.stat().st_size != EXPECTED_BYTES or sha(archive) != EXPECTED_SHA:
        raise ValueError("UPSTREAM_SHA_OR_SIZE_MISMATCH")
    chosen = {}
    total = 0
    with tarfile.open(archive, "r:bz2") as tar:
        for member in tar:
            name = PurePosixPath(member.name)
            if not name.parts or name.parts[0] != MODEL_DIR:
                raise ValueError("UNEXPECTED_ROOT")
            if name.is_absolute() or ".." in name.parts or member.issym() or member.islnk():
                raise ValueError("UNSAFE_ENTRY")
            if member.isdir():
                continue
            if not member.isfile():
                raise ValueError("UNSAFE_ARCHIVE_ENTRY_TYPE")
            if member.size > MAX_FILE:
                raise ValueError("OVERSIZED_ENTRY")
            total += member.size
            if total > MAX_UNPACK:
                raise ValueError("ARCHIVE_TOO_LARGE")
            if len(name.parts) != 2:
                # Example test_wavs/* is excluded; no symlinks permitted.
                continue
            if name.parts[-1] not in EXPECTED_FILES:
                continue
            if name.parts[-1] in chosen:
                raise ValueError("DUPLICATE_CRITICAL_FILE")
            chosen[name.parts[-1]] = member
        if set(chosen) != EXPECTED_FILES:
            raise ValueError("REQUIRED_MODEL_FILES_MISSING")
        bytes_needed = sum(x.size for x in chosen.values())
        if shutil.disk_usage(out.parent).free < bytes_needed + 2_000_000_000:
            raise ValueError("INSUFFICIENT_HOST_FREE_SPACE")
        out.mkdir(parents=True)
        records = []
        try:
            for file in sorted(chosen):
                dest = out / file
                with tar.extractfile(chosen[file]) as src, dest.open("xb") as target:
                    shutil.copyfileobj(src, target, 1024 * 1024)
                if dest.stat().st_size != chosen[file].size:
                    raise ValueError("FILE_SIZE_MISMATCH")
                records.append({"path": file, "bytes": dest.stat().st_size,
                                "sha256": sha(dest)})
            if not (out / "LICENSE").read_text(encoding="utf-8").find("Apache") >= 0:
                raise ValueError("MODEL_LICENSE_NOT_EXPECTED")
            result = {"schema_version": "1.0.0", "model_id": PUBLIC_ID,
                      "directory": "sherpa-omni300m-ctc-int8",
                      "runtime": "sherpa-onnx-v1.13.8-android",
                      "source": SOURCE, "archive_sha256": EXPECTED_SHA,
                      "archive_bytes": EXPECTED_BYTES, "license": "apache-2.0",
                      "total_expanded_bytes": sum(r["bytes"] for r in records),
                      "files": records}
            manifest.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
            return result
        except BaseException:
            # Do not silently delete outputs. Leave staging for audit.
            raise

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--archive", type=Path, required=True)
    p.add_argument("--out", type=Path, required=True)
    p.add_argument("--manifest", type=Path, required=True)
    args = p.parse_args()
    receipt = prepare(args.archive, args.out, args.manifest)
    print(json.dumps({"model_id": receipt["model_id"],
                      "archive_hash_pass": True,
                      "files": len(receipt["files"]),
                      "weight_and_metadata_bytes": receipt["total_expanded_bytes"],
                      "phone_effects": 0}, indent=2))

if __name__ == "__main__":
    main()
