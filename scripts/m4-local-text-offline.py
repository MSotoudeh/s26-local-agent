from __future__ import annotations

import base64
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = r"C:\Tools\Android\platform-tools\adb.exe"
URI = "content://io.projectir.mobile.ui"
OUT = Path(__file__).resolve().parents[1] / "benchmarks" / "m4" / "20261013-local-text-v10-offline.json"
COMMANDS = [("وضعیت گوشی", "DEVICE_STATUS"),
            ("تنظیمات را باز کن و وارد Connections شو", "OPEN_CONNECTIONS")]


def shell(command: str, timeout: float = 12) -> str:
    cp = subprocess.run([ADB, "shell", command], capture_output=True, timeout=timeout)
    if cp.returncode != 0:
        raise RuntimeError(f"ADB_FAILURE command={command.split()[0]} code={cp.returncode}")
    return cp.stdout.decode("utf-8", errors="strict").strip()


def global_setting(key: str) -> str:
    return shell(f"settings get global {key}").strip()


def state() -> dict:
    data = shell(f"content query --uri {URI}/state")
    if "result=" not in data:
        raise RuntimeError("A11Y_NO_PROVIDER_RESULT")
    packet = json.loads(data.split("result=", 1)[1])
    if packet.get("ok") is not True:
        raise RuntimeError("ACCESSIBILITY_NOT_READY")
    return packet["result"]


def call(method: str, arg: str) -> bool:
    reply = shell(f"content call --uri {URI} --method {method} --arg {arg}")
    return '"ok":true' in reply


def last_task() -> dict:
    raw = shell("run-as io.projectir.mobile cat shared_prefs/local_text_agent.xml")
    root = ET.fromstring(raw)
    fields: dict[str, str] = {}
    for item in root:
        if item.tag == "string":
            fields[item.attrib["name"]] = item.text or ""
        else:
            fields[item.attrib["name"]] = item.attrib.get("value", "")
    return fields


def submit(text: str, expected: str) -> dict:
    previous = last_task()
    old_update = int(previous.get("updated_at", "0"))
    shell("am start -W -n io.projectir.mobile/.MainActivity")
    time.sleep(0.2)
    if state().get("packageName") != "io.projectir.mobile":
        raise RuntimeError("AGENT_ACTIVITY_NOT_FOREGROUND")
    if not call("click_resource", "io.projectir.mobile:id/local_command_input"):
        raise RuntimeError("INPUT_FOCUS_REJECTED")
    encoded = base64.b64encode(text.encode("utf-8")).decode("ascii")
    if not call("input_focused_base64", encoded):
        raise RuntimeError("BASE64_INPUT_REJECTED")
    nodes = state()["nodes"]
    field = [n for n in nodes if n.get("resourceId") == "io.projectir.mobile:id/local_command_input"]
    if len(field) != 1 or field[0].get("text") != text:
        raise RuntimeError("INPUT_NOT_EXACT_UTF8")
    shell("input keyevent 4")
    if not call("click_resource", "io.projectir.mobile:id/local_command_run"):
        raise RuntimeError("RUN_BUTTON_REJECTED")
    for _ in range(30):
        time.sleep(0.15)
        result = last_task()
        if int(result.get("updated_at", "0")) <= old_update:
            continue
        if result.get("status") == "RUNNING":
            continue
        record = {
            "command": expected,
            "observed_command_matches": result.get("command") == expected,
            "status": result.get("status"),
            "detail": result.get("detail"),
            "elapsed_ms": int(result.get("elapsed_ms", "0")),
        }
        record["pass"] = record["observed_command_matches"] and record["status"] == "PASS_VERIFIED"
        if expected == "OPEN_CONNECTIONS" and record["pass"]:
            current = state()
            labels = {n.get("text") for n in current.get("nodes", [])}
            record["page_verified"] = (
                current.get("packageName") == "com.android.settings"
                and {"Connections", "Wi-Fi", "Bluetooth"}.issubset(labels)
            )
            record["pass"] = record["pass"] and record["page_verified"]
        return record
    raise RuntimeError("LOCAL_TASK_RESULT_TIMEOUT")


def restore(original: dict) -> dict:
    air = "enable" if original["airplane_mode_on"] == "1" else "disable"
    wifi = "enable" if original["wifi_on"] == "1" else "disable"
    data = "enable" if original["mobile_data"] == "1" else "disable"
    errors = []
    for instruction in [f"cmd connectivity airplane-mode {air}",
                        f"svc wifi {wifi}", f"svc data {data}"]:
        try:
            shell(instruction)
        except Exception as exc:
            errors.append(type(exc).__name__)
    time.sleep(2)
    observed = {name: global_setting(name) for name in original}
    return {"settings": observed,
            "match": observed == original, "restore_error_types": errors}


def main() -> int:
    connected = shell("pm path io.projectir.mobile")
    if "package:" not in connected:
        raise RuntimeError("APP_NOT_INSTALLED")
    initial = {key: global_setting(key) for key in
               ("airplane_mode_on", "wifi_on", "mobile_data")}
    if initial["airplane_mode_on"] != "0":
        raise RuntimeError("AIRPLANE_ALREADY_ACTIVE")
    report = {
        "experiment": "Local Text Agent v0 strict offline / UTF8 transport",
        "date": "2026-10-13",
        "initial_network": initial,
        "strict_offline_verified": False,
        "commands": [],
        "restore": {},
        "foreground_restored": False,
        "failure_type": None,
    }
    try:
        shell("cmd connectivity airplane-mode enable")
        shell("svc wifi disable")
        shell("svc data disable")
        time.sleep(2)
        offline = {key: global_setting(key) for key in initial}
        route = shell("ip route show default")
        report["strict_offline_verified"] = (
            offline["airplane_mode_on"] == "1"
            and offline["wifi_on"] == "0"
            and offline["mobile_data"] == "0"
            and not route.strip()
        )
        if not report["strict_offline_verified"]:
            raise RuntimeError("OFFLINE_PRECONDITION_FAILED")
        for command_text, expected in COMMANDS:
            report["commands"].append(submit(command_text, expected))
    except Exception as error:
        report["failure_type"] = type(error).__name__
        report["failure_summary"] = str(error)[:120]
    finally:
        report["restore"] = restore(initial)
        try:
            shell("am start -n app.rvx.android.apps.youtube.music/com.google.android.apps.youtube.music.activities.MusicActivity")
            report["foreground_restored"] = True
        except Exception:
            report["foreground_restored"] = False
        OUT.parent.mkdir(parents=True, exist_ok=True)
        OUT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    ok = (report["strict_offline_verified"]
          and report["restore"]["match"]
          and len(report["commands"]) == len(COMMANDS)
          and all(row["pass"] for row in report["commands"]))
    print(json.dumps({
        "pass": ok,
        "strict_offline": report["strict_offline_verified"],
        "restored": report["restore"]["match"],
        "command_results": report["commands"],
        "error": report["failure_type"]
    }, ensure_ascii=False))
    return 0 if ok else 2


if __name__ == "__main__":
    sys.exit(main())
