from __future__ import annotations

import json
import subprocess
import sys
import time
from typing import Any

from host.phone_controller import (
    MobilerunUsbSession,
    PhoneControlError,
    flatten_nodes,
    parse_bounds,
)

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

ADB = r"C:\Tools\Android\platform-tools\adb.exe"


def text_set(state: dict[str, Any]) -> set[str]:
    return {
        str(n.get("text") or "")
        for n in flatten_nodes(state.get("a11y_tree", []))
        if str(n.get("text") or "")
    }


def find_connections_target(state: dict[str, Any]) -> dict[str, Any] | None:
    candidates = [
        n for n in flatten_nodes(state.get("a11y_tree", []))
        if n.get("text") == "Connections"
    ]
    if not candidates:
        return None

    # Prefer the homepage row over a toolbar/title duplicate.
    ranked = []
    for node in candidates:
        try:
            x1, y1, x2, y2 = parse_bounds(str(node.get("bounds") or ""))
            ranked.append((y1 >= 500, (x2 - x1) * (y2 - y1), node))
        except Exception:
            continue
    if not ranked:
        return candidates[0]
    ranked.sort(key=lambda item: (item[0], item[1]), reverse=True)
    return ranked[0][2]


out: dict[str, Any] = {}

with MobilerunUsbSession(adb=ADB) as s:
    try:
        # Normalize the Settings task while the controller session is already ready.
        subprocess.run(
            [ADB, "shell", "am", "start", "-a", "android.settings.SETTINGS"],
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )

        backs = 0
        deadline = time.monotonic() + 2.0
        while time.monotonic() < deadline:
            try:
                phone = s.phone_state()
            except Exception:
                time.sleep(0.03)
                continue
            pkg = phone.get("packageName")
            if pkg == "com.android.settings":
                break
            if pkg == "com.android.settings.intelligence" and backs < 3:
                s.global_action(1)
                backs += 1
                time.sleep(0.08)
                continue
            time.sleep(0.03)

        out["normalization_backs"] = backs

        top_scrolls = 0
        target = None
        state: dict[str, Any] = {}
        for top_scrolls in range(4):
            t0 = time.perf_counter_ns()
            state = s.state()
            t1 = time.perf_counter_ns()
            target = find_connections_target(state)
            if target is not None:
                break
            s.swipe(540, 650, 540, 1900, 250)
            time.sleep(0.10)

        if target is None:
            raise PhoneControlError("Connections target not found after root normalization")

        x1, y1, x2, y2 = parse_bounds(str(target.get("bounds")))

        t2 = time.perf_counter_ns()
        s.tap((x1 + x2) // 2, (y1 + y2) // 2)
        t3 = time.perf_counter_ns()

        deadline = time.monotonic() + 2.0
        polls = 0
        verified = False
        while time.monotonic() < deadline:
            polls += 1
            verify_state = s.state()
            visible = text_set(verify_state)
            if "Wi-Fi" in visible and "Bluetooth" in visible:
                verified = True
                break
            time.sleep(0.02)
        t4 = time.perf_counter_ns()

        s.global_action(1)
        deadline = time.monotonic() + 2.0
        back_verified = False
        while time.monotonic() < deadline:
            st = s.state()
            visible = text_set(st)
            if "Connections" in visible and "Wi-Fi" not in visible:
                back_verified = True
                break
            time.sleep(0.02)
        t5 = time.perf_counter_ns()

        out.update({
            "top_scroll_attempts": top_scrolls,
            "initial_state_ms": round((t1 - t0) / 1e6, 2),
            "tap_request_ms": round((t3 - t2) / 1e6, 2),
            "tap_to_verified_ms": round((t4 - t3) / 1e6, 2),
            "verify_polls": polls,
            "verified_connections": verified,
            "back_to_verified_ms": round((t5 - t4) / 1e6, 2),
            "verified_back": back_verified,
        })
    finally:
        try:
            restored = s.launch_app_verified("com.openai.chatgpt", timeout=3.0)
            out["restored"] = restored["phone_state"].get("packageName")
        except Exception as exc:
            out["restore_error"] = str(exc)

print(json.dumps(out, ensure_ascii=False, indent=2))
