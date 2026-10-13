from __future__ import annotations

import base64
import json
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = r"C:\Tools\Android\platform-tools\adb.exe"
URI = "content://io.projectir.mobile.ui"
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "benchmarks" / "m4" / "20261013-local-v1b-replay-cancel.json"


def adb_shell(command: str, timeout: float = 25) -> str:
    p = subprocess.run([ADB, "shell", command], capture_output=True, timeout=timeout)
    if p.returncode:
        raise RuntimeError("ADB_FAIL:" + command.split(" ", 1)[0])
    return p.stdout.decode("utf-8", errors="strict").strip()


def call(method: str, argument: str) -> bool:
    return '"ok":true' in adb_shell(
        f"content call --uri {URI} --method {method} --arg {argument}"
    )


def put_text(resource_id: str, value: str) -> None:
    if not call("click_resource", "io.projectir.mobile:id/" + resource_id):
        raise RuntimeError("NOT_FOCUSABLE:" + resource_id)
    b64 = base64.b64encode(value.encode("utf-8")).decode("ascii")
    if not call("input_focused_base64", b64):
        raise RuntimeError("NO_TEXT_INPUT:" + resource_id)
    adb_shell("input keyevent 4")


def prefs() -> dict[str, str]:
    raw = adb_shell("run-as io.projectir.mobile cat shared_prefs/local_text_agent.xml")
    return {n.attrib["name"]: n.attrib.get("value", n.text or "")
            for n in ET.fromstring(raw)}


def run() -> dict:
    if not call("click_resource", "io.projectir.mobile:id/local_command_run"):
        raise RuntimeError("RUN_CLICK_REJECTED")
    return prefs()


def cancel() -> dict:
    if not call("click_resource", "io.projectir.mobile:id/local_command_cancel"):
        raise RuntimeError("CANCEL_CLICK_REJECTED")
    return prefs()


report = {
    "date": "2026-10-13",
    "test": "replay same Action ID and cancel local asynchronous timer",
    "replay": {"pass": False},
    "cancel": {"pass": False},
    "completion": {"pass": False},
    "restored_foreground": False,
    "failure_type": None,
}
try:
    adb_shell("am start -W -n io.projectir.mobile/.MainActivity")
    time.sleep(0.35)
    put_text("local_command_input", "device status")
    put_text("local_action_id_input", "v1b_replay_00001")
    first = run()
    first_ok = first.get("status") == "PASS_VERIFIED" and first.get("command") == "DEVICE_STATUS"
    action_id = first.get("last_action_id", "")
    old_update = first.get("updated_at", "")
    old_journal = first.get("recent_action_ids", "")
    second = run()
    report["replay"] = {
        "pass": first_ok and action_id == "v1b_replay_00001"
                and second.get("status") == "REJECTED_DUPLICATE"
                and second.get("last_action_id") == action_id
                and second.get("recent_action_ids") == old_journal
                and second.get("updated_at") != old_update,
        "first_status": first.get("status"),
        "second_status": second.get("status"),
        "last_id_unchanged": second.get("last_action_id") == action_id,
        "journal_unchanged": second.get("recent_action_ids") == old_journal,
    }
    if not report["replay"]["pass"]:
        raise RuntimeError("REPLAY_ACCEPTANCE_FAILED")

    put_text("local_command_input", "wait five seconds")
    put_text("local_action_id_input", "")
    before = time.monotonic()
    running = run()
    cancelled = cancel()
    time.sleep(5.35)
    after = prefs()
    report["cancel"] = {
        "pass": running.get("status") == "RUNNING"
                and cancelled.get("status") == "CANCELLED"
                and after.get("status") == "CANCELLED"
                and cancelled.get("last_action_id") == after.get("last_action_id"),
        "started": running.get("status"),
        "cancel_response": cancelled.get("status"),
        "state_after_timer": after.get("status"),
        "cancel_latency_ms_from_run": round((time.monotonic() - before) * 1000)
    }
    if not report["cancel"]["pass"]:
        raise RuntimeError("CANCEL_ACCEPTANCE_FAILED")

    start = time.monotonic()
    running2 = run()
    time.sleep(5.40)
    final = prefs()
    report["completion"] = {
        "pass": running2.get("status") == "RUNNING"
                and final.get("status") == "PASS_VERIFIED"
                and final.get("command") == "WAIT_FIVE_SECONDS"
                and final.get("last_action_id") != after.get("last_action_id"),
        "started": running2.get("status"),
        "status_after_timer": final.get("status"),
        "duration_ms": final.get("elapsed_ms"),
        "unique_action_id": final.get("last_action_id") != after.get("last_action_id")
    }
except Exception as e:
    report["failure_type"] = type(e).__name__
    report["failure_class"] = str(e)[:100]
finally:
    try:
        adb_shell("am start -n app.rvx.android.apps.youtube.music/com.google.android.apps.youtube.music.activities.MusicActivity")
        report["restored_foreground"] = True
    except Exception:
        report["restored_foreground"] = False
    report["pass"] = all(report[name].get("pass", False) for name in ("replay","cancel","completion"))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps({k: v for k,v in report.items() if k != "failure_class"}, ensure_ascii=False))
raise SystemExit(0 if report["pass"] else 2)
