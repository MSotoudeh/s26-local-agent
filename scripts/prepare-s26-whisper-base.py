#!/usr/bin/env python3
"""Prepare Apache-2.0 public Whisper-base multilingual Int8 model from official
sherpa-onnx GitHub archive. Hash pinned, allowlisted 3 files, no phone writes.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import tarfile

FOLDER = "sherpa-onnx-whisper-base"
MODEL_ID = "asr.whisper.base.int8"
URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-base.tar.bz2"
SHA256 = "911b2083efd7c0dca2ac3b358b75222660dc09fb716d64fbfc417ba6c99ff3de"
ARCHIVE_BYTES = 207557382
FILES = {"base-encoder.int8.onnx", "base-decoder.int8.onnx", "base-tokens.txt"}

def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1048576), b""):
            h.update(chunk)
    return h.hexdigest()

def prepare(archive, dest, manifest):
    if dest.exists() or manifest.exists():
        raise RuntimeError("DESTINATION_ALREADY_EXISTS_REQUIRES_INSPECTION")
    if archive.stat().st_size != ARCHIVE_BYTES or digest(archive) != SHA256:
        raise RuntimeError("UNEXPECTED_UPSTREAM_ARCHIVE_HASH_OR_SIZE")
    found = {}
    total = 0
    with tarfile.open(archive, "r:bz2") as tf:
        for member in tf:
            path = PurePosixPath(member.name)
            if not path.parts or path.parts[0] != FOLDER or path.is_absolute() or ".." in path.parts:
                raise RuntimeError("UNSAFE_ARCHIVE_ROOT")
            if member.issym() or member.islnk() or not (member.isfile() or member.isdir()):
                raise RuntimeError("UNSAFE_ARCHIVE_ENTRY")
            if member.isdir():
                continue
            if member.size < 0 or member.size > 500_000_000:
                raise RuntimeError("INVALID_FILE_LENGTH")
            total += member.size
            if total > 1_000_000_000:
                raise RuntimeError("EXCESSIVE_EXTRACTED_SIZE")
            if len(path.parts) == 2 and path.parts[1] in FILES:
                key = path.parts[1]
                if key in found:
                    raise RuntimeError("DUPLICATE_MODEL_COMPONENT")
                found[key] = member
        if set(found) != FILES:
            raise RuntimeError("MODEL_COMPONENTS_NOT_FOUND")
        if shutil.disk_usage(dest.parent).free < 2_000_000_000 + sum(x.size for x in found.values()):
            raise RuntimeError("HOST_STORAGE_GUARD")
        dest.mkdir(parents=True)
        filelist = []
        for name in sorted(FILES):
            output = dest / name
            with tf.extractfile(found[name]) as src, output.open("xb") as dst:
                shutil.copyfileobj(src, dst, 1024 * 1024)
            if output.stat().st_size != found[name].size:
                raise RuntimeError("EXTRACTED_FILE_LENGTH_MISMATCH")
            filelist.append({"path": name, "bytes": output.stat().st_size,
                             "sha256": digest(output)})
        record = {"schema_version": "1.0.0", "model_id": MODEL_ID,
                  "directory": "sherpa-whisper-base-int8",
                  "runtime": "sherpa-onnx-v1.13.8-android",
                  "source": URL, "archive_sha256": SHA256,
                  "archive_bytes": ARCHIVE_BYTES,
                  "license": "apache-2.0",
                  "license_source": "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/blob/31dc0cc3b59ba4a0b52941f1d6dce9c0e30311b4/README.md",
                  "total_expanded_bytes": sum(f["bytes"] for f in filelist),
                  "files": filelist}
        manifest.parent.mkdir(parents=True, exist_ok=True)
        manifest.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
        return record

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--archive", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    args = parser.parse_args()
    receipt = prepare(args.archive, args.out, args.manifest)
    print(json.dumps({"id": receipt["model_id"],
                      "files": len(receipt["files"]),
                      "expanded_bytes": receipt["total_expanded_bytes"],
                      "archive_sha256_verified": True,
                      "phone_effects": 0}, indent=2))
