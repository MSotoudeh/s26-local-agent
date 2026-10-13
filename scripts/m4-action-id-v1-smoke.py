from __future__ import annotations

import base64
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = r"C:\Tools\Android\platform-tools\adb.exe"
URI = "content://io.projectir.mobile.ui"
OUT = Path(__file__).resolve().parents[1] / "benchmarks" / "m4" / "20261013-action-id-v1-smoke.json"
TRIALS = 30


def shell(command: str) -> str:
    p = subprocess.run([ADB, "shell", command], capture_output=True, timeout=15)
    if p.returncode != 0:
        raise RuntimeError("ADB_FAILURE")
    return p.stdout.decode("utf-8", errors="strict").strip()


def call(method: str, value: str) -> None:
    output = shell(f"content call --uri {URI} --method {method} --arg {value}")
    if '"ok":true' not in output:
        raise RuntimeError("UI_ACTION_REJECTED")


def snapshot_package() -> str:
    output = shell(f"content query --uri {URI}/state")
    info = json.loads(output.split("result=", 1)[1])
    if not info.get("ok"):
        raise RuntimeError("A11Y_NOT_READY")
    return str(info["result"].get("packageName"))


def prefs() -> dict[str, str]:
    raw = shell("run-as io.projectir.mobile cat shared_prefs/local_text_agent.xml")
    doc = ET.fromstring(raw)
    result = {}
    for entry in doc:
        result[entry.attrib["name"]] = entry.attrib.get("value", entry.text or "")
    return result


def main() -> int:
    result = {"date": "2026-10-13",
              "experiment": "Local Text Agent v1 action identity / local status",
              "trials_requested": TRIALS, "trials_completed": 0,
              "unique_action_ids": True, "all_verified": True,
              "elapsed_ms": [], "error_type": None,
              "restored_launcher": False}
    seen: set[str] = set()
    try:
        shell("am start -W -n io.projectir.mobile/.MainActivity")
        previous = prefs().get("last_action_id", "")
        for _ in range(TRIALS):
            if snapshot_package() != "io.projectir.mobile":
                raise RuntimeError("UNEXPECTED_FOREGROUND")
            call("click_resource", "io.projectir.mobile:id/local_command_input")
            payload = base64.b64encode(b"device status").decode("ascii")
            call("input_focused_base64", payload)
            shell("input keyevent 4")
            call("click_resource", "io.projectir.mobile:id/local_command_run")
            deadline = time.monotonic() + 4
            current = {}
            while time.monotonic() < deadline:
                current = prefs()
                if (current.get("status") == "PASS_VERIFIED"
                        and current.get("command") == "DEVICE_STATUS"
                        and current.get("last_action_id") not in ("", previous)):
                    break
                time.sleep(0.08)
            else:
                raise RuntimeError("POSTCONDITION_NOT_VERIFIED")
            action_id = current["last_action_id"]
            result["unique_action_ids"] &= action_id not in seen
            seen.add(action_id)
            previous = action_id
            result["trials_completed"] += 1
            result["elapsed_ms"].append(int(current.get("elapsed_ms", "0")))
            journal = current.get("recent_action_ids", "").splitlines()
            if action_id not in journal or len(journal) > 64:
                raise RuntimeError("JOURNAL_INVARIANT_FAILED")
    except Exception as error:
        result["error_type"] = type(error).__name__
        result["failure_reason"] = str(error)[:80]
    finally:
        try:
            shell("input keyevent 3")
            result["restored_launcher"] = True
        except Exception:
            pass
        result["elapsed_ms_min"] = min(result["elapsed_ms"], default=None)
        result["elapsed_ms_max"] = max(result["elapsed_ms"], default=None)
        result.pop("elapsed_ms")
    result["pass"] = (result["trials_completed"] == TRIALS
                      and result["unique_action_ids"]
                      and result["all_verified"]
                      and result["error_type"] is None)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in result.items()
                      if k != "failure_reason"}, ensure_ascii=False))
    return 0 if result["pass"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
