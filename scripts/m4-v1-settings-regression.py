from __future__ import annotations

import importlib.util
import json
import sys
from pathlib import Path

BASE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location("m4_offline", BASE / "m4-local-text-offline.py")
assert spec and spec.loader
module=importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
DEST=BASE.parent / "benchmarks" / "m4" / "20261013-reliable-v1-settings-regression-retry.json"
TRIALS=5
report={"date":"2026-10-13","test":"Local Executor v1 Settings Connections",
        "planned":TRIALS,"results":[],"unique_action_ids":True,
        "restored_launcher":False,"error_type":None}
known=set()
try:
    for _ in range(TRIALS):
        row=module.submit("تنظیمات را باز کن و وارد Connections شو","OPEN_CONNECTIONS")
        prefs=module.last_task()
        identifier=prefs.get("last_action_id","")
        is_new=bool(identifier) and identifier not in known
        known.add(identifier)
        report["unique_action_ids"] &= is_new
        report["results"].append({"passed":row["pass"] and is_new,
                                  "elapsed_ms":row.get("elapsed_ms"),
                                  "verified_page":row.get("page_verified",False),
                                  "unique_id":is_new})
        if not report["results"][-1]["passed"]:
            raise RuntimeError("FAILED_POSTCONDITION")
except Exception as exc:
    report["error_type"]=type(exc).__name__
finally:
    try:
        module.shell("input keyevent 3")
        report["restored_launcher"]=True
    except Exception:
        pass
    DEST.parent.mkdir(parents=True,exist_ok=True)
    DEST.write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
success=len(report["results"])==TRIALS and all(x["passed"] for x in report["results"]) and report["restored_launcher"] and report["error_type"] is None
print(json.dumps({"pass":success,"completed":len(report["results"]),"unique":report["unique_action_ids"],"results":report["results"],"restored_launcher":report["restored_launcher"],"error_type":report["error_type"]}))
sys.exit(0 if success else 2)
