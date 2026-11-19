#!/usr/bin/env python3
"""Read-only 16 KiB ELF + APK ZIP page alignment audit; no external dependencies."""
import pathlib
import struct
import sys
import zipfile

PAGE = 16384

def analyze_elf(blob):
    if blob[:4] != b"\x7fELF":
        return {"error": "NOT_ELF"}
    is64 = blob[4] == 2
    endian = "<" if blob[5] == 1 else ">"
    if not is64:
        return {"error": "NOT_ELF64"}
    phoff = struct.unpack_from(endian + "Q", blob, 32)[0]
    phentsize, phnum = struct.unpack_from(endian + "HH", blob, 54)
    aligns = []
    offsets = []
    for idx in range(phnum):
        pos = phoff + idx * phentsize
        if pos + 56 > len(blob):
            return {"error":"TRUNCATED"}
        p_type, p_flags, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_align = struct.unpack_from(endian + "IIQQQQQQ", blob, pos)
        if p_type == 1:
            aligns.append(p_align)
            offsets.append((p_offset - p_vaddr) % PAGE)
    return {"load_aligns": aligns, "load_offset_mod": offsets,
            "compatible": bool(aligns) and min(aligns) >= PAGE and all(x == 0 for x in offsets)}

def audit(path):
    with zipfile.ZipFile(path) as z:
        found = []
        for entry in z.infolist():
            if not entry.filename.endswith(".so") or "/arm64-v8a/" not in entry.filename:
                continue
            head = entry.header_offset + 30 + len(entry.orig_filename.encode("utf8")) + len(entry.extra)
            item = analyze_elf(z.read(entry.filename))
            item["library"] = entry.filename.split("/")[-1]
            item["zip_page_aligned"] = head % PAGE == 0
            item["zip_method"] = entry.compress_type
            item["size_mb"] = round(entry.file_size / 1048576, 2)
            found.append(item)
    print("APK",path.name,"size_MB",round(path.stat().st_size/1048576,2))
    print("NATIVE_LIBRARIES",len(found))
    for item in found:
        print(item)
    if not found: raise RuntimeError("No 64bit native library found")
    print("ALL_16K_ELF_PASS", all(x["compatible"] for x in found))

if __name__ == "__main__":
    for arg in sys.argv[1:]:
        audit(pathlib.Path(arg))
