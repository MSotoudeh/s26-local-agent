from __future__ import annotations
import importlib.util
import json
import sys
from pathlib import Path

HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location("m4offline", HERE / "m4-local-text-offline.py")
assert spec is not None and spec.loader is not None
mod=importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

CASES=[
    ("ماشین حساب را باز کن","OPEN_CALCULATOR","com.sec.android.app.popupcalculator"),
    ("ساعت را باز کن","OPEN_CLOCK","com.sec.android.app.clockpackage"),
    ("نقشه را باز کن","OPEN_MAPS","com.google.android.apps.maps"),
]
report={"experiment":"Local Text Agent v0 app launcher smoke",
        "date":"2026-10-13","radio_changes":False,"commands":[],"foreground_restored":False}
try:
    for text,kind,pkg in CASES:
        row=mod.submit(text,kind)
        if row.get("pass"):
            ui=mod.state()
            row["foreground_verified"]=ui.get("packageName")==pkg
            row["pass"]=row["pass"] and row["foreground_verified"]
        report["commands"].append(row)
finally:
    try:
        mod.shell("am start -n app.rvx.android.apps.youtube.music/com.google.android.apps.youtube.music.activities.MusicActivity")
        report["foreground_restored"]=True
    except Exception:
        report["foreground_restored"]=False
    dest=HERE.parents[0]/"benchmarks"/"m4"/"20261013-local-app-launch-smoke.json"
    dest.parent.mkdir(parents=True,exist_ok=True)
    dest.write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
ok=all(x.get("pass") for x in report["commands"]) and len(report["commands"])==len(CASES) and report["foreground_restored"]
print(json.dumps({"pass":ok,"commands":report["commands"],"restored":report["foreground_restored"]},ensure_ascii=False))
sys.exit(0 if ok else 2)
