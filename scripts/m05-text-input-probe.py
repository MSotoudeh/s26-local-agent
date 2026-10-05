from __future__ import annotations

import json
import subprocess
import sys
import time
from typing import Any

from host.phone_controller import MobilerunUsbSession, flatten_nodes, parse_bounds

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")


def center(node: dict[str, Any]) -> tuple[int, int]:
    x1, y1, x2, y2 = parse_bounds(str(node.get("bounds") or ""))
    return (x1 + x2) // 2, (y1 + y2) // 2


def find_by_resource(state: dict[str, Any], resource: str) -> dict[str, Any] | None:
    for node in flatten_nodes(state.get("a11y_tree", [])):
        rid = node.get("viewIdResourceName") or node.get("resourceId")
        if rid == resource:
            return node
    return None


out: dict[str, Any] = {}

with MobilerunUsbSession() as s:
    try:
        subprocess.run(
            [s.adb, "shell", "am", "start", "-a", "android.settings.SETTINGS"],
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )

        deadline = time.monotonic() + 2.0
        backs = 0
        root_phone: dict[str, Any] = {}
        while time.monotonic() < deadline:
            try:
                root_phone = s.phone_state()
            except Exception:
                time.sleep(0.03)
                continue

            pkg = root_phone.get("packageName")
            if pkg == "com.android.settings":
                break

            if pkg == "com.android.settings.intelligence" and backs < 3:
                s.global_action(1)
                backs += 1
                time.sleep(0.08)
                continue

            time.sleep(0.03)

        out["normalization_backs"] = backs
        out["root_package"] = root_phone.get("packageName")

        search_state: dict[str, Any] = {}
        field: dict[str, Any] | None = None
        transition_attempts = 0

        for transition_attempts in range(1, 4):
            root = s.state()
            entry = find_by_resource(
                root,
                "com.android.settings:id/search_src_text",
            )
            if entry is None:
                break

            s.tap(*center(entry))
            deadline = time.monotonic() + 0.8

            while time.monotonic() < deadline:
                try:
                    phone = s.phone_state()
                except Exception:
                    time.sleep(0.03)
                    continue

                if phone.get("packageName") == "com.android.settings.intelligence":
                    search_state = s.state()
                    field = find_by_resource(
                        search_state,
                        "com.android.settings.intelligence:id/search_src_text",
                    )
                    if field is not None:
                        break

                time.sleep(0.03)

            if field is not None:
                break

            time.sleep(0.06)

        out["search_transition_attempts"] = transition_attempts
        out["search_package"] = (
            search_state.get("phone_state") or {}
        ).get("packageName")

        if field is None:
            out["verified"] = False
            out["reason"] = "search editable field not found"
        else:
            s.tap(*center(field))
            time.sleep(0.08)
            focused = s.phone_state()

            out["keyboard_before_input"] = focused.get("keyboardVisible")
            out["is_editable_before_input"] = focused.get("isEditable")
            out["focused_before_input"] = focused.get("focusedElement")

            test_text = "سلام test 123"
            t0 = time.perf_counter_ns()
            response = s.input_text(test_text, clear=True)
            t1 = time.perf_counter_ns()

            time.sleep(0.12)
            verify = s.state()
            values = [
                str(n.get("text") or "")
                for n in flatten_nodes(verify.get("a11y_tree", []))
            ]

            out["response"] = response
            out["request_ms"] = round((t1 - t0) / 1e6, 2)
            out["verified"] = any(test_text in value for value in values)
            out["matching_values"] = [
                value for value in values if test_text in value
            ][:5]

            try:
                s.input_text("", clear=True)
            except Exception:
                pass
    finally:
        try:
            restored = s.launch_app_verified(
                "com.openai.chatgpt",
                timeout=3.0,
            )
            out["restored"] = restored["phone_state"].get("packageName")
        except Exception as exc:
            out["restore_error"] = str(exc)

print(json.dumps(out, ensure_ascii=False, indent=2))
