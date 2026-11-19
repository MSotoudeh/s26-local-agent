#!/usr/bin/env python3
"""Strictly prepare pinned public Qwen3-ASR 0.6B ONNX without phone effects."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import tarfile

ORIGIN = "sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25"
MODEL_ID = "asr.qwen3.0_6b.int8"
DEST_NAME = "sherpa-qwen3-asr-0.6b-int8"
REQUIRED = {"conv_frontend.onnx", "encoder.int8.onnx",
            "decoder.int8.onnx"}

def digest(path):
    h=hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda:f.read(1048576),b""): h.update(block)
    return h.hexdigest()

def prepare(archive, dest, manifest, lock):
    meta=json.loads(lock.read_text(encoding="utf-8"))
    expected=meta.get("sha256","")
    if not re.fullmatch(r"[a-f0-9]{64}",expected):
        raise ValueError("RELEASE_SHA256_NOT_PINNED")
    if meta.get("model_id")!=MODEL_ID or meta.get("bytes")!=878702423:
        raise ValueError("RELEASE_ID_OR_LENGTH_MISMATCH")
    if archive.stat().st_size!=meta["bytes"] or digest(archive)!=expected:
        raise ValueError("ARCHIVE_SHA256_MISMATCH")
    if dest.exists() or manifest.exists():
        raise FileExistsError("OUTPUT_COLLISION")
    if shutil.disk_usage(dest.parent).free<4000000000:
        raise ValueError("DISK_BUDGET")
    files={}
    expanded=0
    with tarfile.open(archive,"r|bz2") as stream:
        dest.mkdir(parents=True)
        for member in stream:
            p=PurePosixPath(member.name)
            if not p.parts or p.parts[0]!=ORIGIN or p.is_absolute() or ".." in p.parts:
                raise ValueError("UNSAFE_ARCHIVE_PATH")
            if member.issym() or member.islnk() or not (member.isdir() or member.isfile()):
                raise ValueError("UNSAFE_ARCHIVE_MEMBER")
            if member.isdir(): continue
            rel=PurePosixPath(*p.parts[1:])
            name=rel.as_posix()
            allowed=name in REQUIRED or (
                len(rel.parts)==2 and rel.parts[0]=="tokenizer"
                and re.fullmatch(r"[a-zA-Z0-9_.-]{1,120}",rel.parts[-1]))
            if not allowed: continue
            if name in files or member.size<1 or member.size>1900000000:
                raise ValueError("FILE_DUPLICATE_OR_SIZE")
            expanded+=member.size
            if expanded>2500000000 or len(files)>=100:
                raise ValueError("EXPANSION_BUDGET")
            target=dest.joinpath(*rel.parts)
            target.parent.mkdir(parents=True,exist_ok=True)
            with stream.extractfile(member) as source,target.open("xb") as output:
                shutil.copyfileobj(source,output,1048576)
            if target.stat().st_size!=member.size:
                raise ValueError("EXTRACT_LENGTH_MISMATCH")
            files[name]={"path":name,"bytes":member.size,"sha256":digest(target)}
    if not REQUIRED.issubset(files) or not any(k.startswith("tokenizer/") for k in files):
        raise ValueError("REQUIRED_FILES_MISSING")
    result={"schema_version":"1.0.0","model_id":MODEL_ID,
            "directory":DEST_NAME,"runtime":"sherpa-onnx-v1.13.8-android",
            "source":meta["source"],"archive_sha256":expected,
            "archive_bytes":meta["bytes"],"license":"apache-2.0",
            "total_expanded_bytes":expanded,
            "files":[files[k] for k in sorted(files)]}
    manifest.parent.mkdir(parents=True,exist_ok=True)
    manifest.write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
    return result

if __name__=="__main__":
    p=argparse.ArgumentParser()
    for opt in ("archive","out","manifest","lock"):
        p.add_argument("--"+opt,type=Path,required=True)
    args=p.parse_args()
    r=prepare(args.archive,args.out,args.manifest,args.lock)
    print(json.dumps({"verified_archive":True,
                      "model_id":r["model_id"],"files":len(r["files"]),
                      "expanded_bytes":r["total_expanded_bytes"]},indent=2))
