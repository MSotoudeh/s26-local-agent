"""Minimal USB phone-control adapter for S26 feasibility experiments.

Lab-only backend: Mobilerun Portal. The adapter:
- discovers ADB;
- enables Mobilerun's local HTTP server only for the session;
- forwards it over USB;
- obtains the documented local auth token without persisting or printing it;
- exposes state and bounded actions;
- disables the server and removes the forward on exit.

No third-party Python packages are required.
"""
from __future__ import annotations

import argparse
import base64
import http.client
import json
import math
import os
import re
import shutil
import statistics
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable
from urllib.parse import urlencode


MOBILERUN_PACKAGE = "com.mobilerun.portal"
HTTP_PORT = 8080
DEFAULT_FORWARD_PORT = 18080


class PhoneControlError(RuntimeError):
    pass


def find_adb() -> str:
    found = shutil.which("adb")
    candidates = [
        found,
        r"C:\Tools\Android\platform-tools\adb.exe",
        str(Path(os.environ.get("LOCALAPPDATA", "")) / "Android/Sdk/platform-tools/adb.exe"),
        str(
            Path(os.environ.get("LOCALAPPDATA", ""))
            / "Microsoft/WinGet/Packages/Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe/platform-tools/adb.exe"
        ),
        r"C:\platform-tools\adb.exe",
    ]
    for candidate in candidates:
        if candidate and Path(candidate).is_file():
            return candidate
    raise PhoneControlError("ADB executable not found")


def parse_provider_token(output: str) -> str:
    match = re.search(r"result=(\{.*\})\s*$", output.strip(), re.DOTALL)
    if not match:
        raise PhoneControlError("Could not parse Mobilerun auth-token response")
    outer = json.loads(match.group(1))
    token = outer.get("result")
    if outer.get("status") != "success" or not isinstance(token, str) or not token:
        raise PhoneControlError("Mobilerun auth token unavailable")
    return token


def unwrap_api(payload: Any) -> Any:
    if isinstance(payload, dict) and "result" in payload:
        result = payload["result"]
        if isinstance(result, str):
            stripped = result.strip()
            if stripped.startswith("{") or stripped.startswith("["):
                try:
                    return json.loads(stripped)
                except json.JSONDecodeError:
                    return result
        return result
    return payload


def parse_bounds(bounds: str) -> tuple[int, int, int, int]:
    nums = [int(x) for x in re.findall(r"\d+", bounds)]
    if len(nums) != 4:
        raise PhoneControlError(f"Invalid bounds: {bounds!r}")
    return nums[0], nums[1], nums[2], nums[3]


def flatten_nodes(nodes: Any) -> Iterable[dict[str, Any]]:
    if isinstance(nodes, list):
        for node in nodes:
            yield from flatten_nodes(node)
        return
    if not isinstance(nodes, dict):
        return
    yield nodes
    children = nodes.get("children")
    if isinstance(children, list):
        for child in children:
            yield from flatten_nodes(child)


@dataclass
class Timing:
    name: str
    values_ms: list[float]

    def summary(self) -> dict[str, float | int | str]:
        vals = sorted(self.values_ms)
        if not vals:
            return {"name": self.name, "n": 0}
        def nearest_rank(q: float) -> float:
            i = max(0, min(len(vals) - 1, math.ceil(q * len(vals)) - 1))
            return round(vals[i], 2)
        return {
            "name": self.name,
            "n": len(vals),
            "p50_ms": nearest_rank(0.50),
            "p95_ms": nearest_rank(0.95),
            "min_ms": round(vals[0], 2),
            "max_ms": round(vals[-1], 2),
        }


class MobilerunUsbSession:
    def __init__(self, adb: str | None = None, forward_port: int = DEFAULT_FORWARD_PORT):
        self.adb = adb or find_adb()
        self.forward_port = forward_port
        self.token: str | None = None
        self._open = False

    def _adb(self, *args: str, timeout: float = 8.0) -> str:
        cp = subprocess.run(
            [self.adb, *args],
            check=False,
            capture_output=True,
            text=True,
            timeout=timeout,
        )
        if cp.returncode != 0:
            raise PhoneControlError(
                f"ADB command failed ({cp.returncode}): {' '.join(args)}\n{cp.stderr.strip()}"
            )
        return cp.stdout.strip()

    def doctor(self) -> dict[str, Any]:
        devices = self._adb("devices", "-l")
        authorized = [
            line for line in devices.splitlines()
            if re.search(r"\sdevice(?:\s|$)", line) and not line.startswith("List of devices")
        ]
        package = self._adb("shell", "pm", "list", "packages", MOBILERUN_PACKAGE)
        return {
            "adb": self.adb,
            "authorized_device_count": len(authorized),
            "mobilerun_installed": f"package:{MOBILERUN_PACKAGE}" in package,
        }

    def open(self) -> "MobilerunUsbSession":
        if self._open:
            return self
        status = self.doctor()
        if status["authorized_device_count"] != 1:
            raise PhoneControlError(
                f"Expected exactly one authorized ADB device; got {status['authorized_device_count']}"
            )
        if not status["mobilerun_installed"]:
            raise PhoneControlError("Mobilerun Portal is not installed")

        self._adb(
            "shell", "content", "insert",
            "--uri", f"content://{MOBILERUN_PACKAGE}/toggle_socket_server",
            "--bind", "enabled:b:true",
            "--bind", f"port:i:{HTTP_PORT}",
        )
        subprocess.run(
            [self.adb, "forward", "--remove", f"tcp:{self.forward_port}"],
            check=False,
            capture_output=True,
            text=True,
        )
        self._adb("forward", f"tcp:{self.forward_port}", f"tcp:{HTTP_PORT}")
        raw = self._adb(
            "shell", "content", "query",
            "--uri", f"content://{MOBILERUN_PACKAGE}/auth_token",
        )
        self.token = parse_provider_token(raw)
        self._open = True

        # The Android socket server can lag slightly behind the ContentProvider
        # toggle. Treat that as a bounded startup race, not a hard failure.
        deadline = time.monotonic() + 2.0
        last_error: Exception | None = None
        while time.monotonic() < deadline:
            try:
                self._request("GET", "ping", auth=False, timeout=0.4)
                return self
            except (OSError, TimeoutError, PhoneControlError) as exc:
                last_error = exc
                time.sleep(0.05)

        self.close()
        raise PhoneControlError(
            f"Mobilerun HTTP server did not become ready: {last_error}"
        )

    def close(self) -> None:
        if not self._open:
            return
        try:
            self._adb(
                "shell", "content", "insert",
                "--uri", f"content://{MOBILERUN_PACKAGE}/toggle_socket_server",
                "--bind", "enabled:b:false",
            )
        finally:
            subprocess.run(
                [self.adb, "forward", "--remove", f"tcp:{self.forward_port}"],
                check=False,
                capture_output=True,
                text=True,
            )
            self.token = None
            self._open = False

    def __enter__(self) -> "MobilerunUsbSession":
        return self.open()

    def __exit__(self, exc_type, exc, tb) -> None:
        self.close()

    def _request(
        self,
        method: str,
        path: str,
        *,
        form: dict[str, Any] | None = None,
        auth: bool = True,
        timeout: float = 3.0,
    ) -> Any:
        if not self._open and path != "ping":
            raise PhoneControlError("Session is not open")
        headers: dict[str, str] = {}
        if auth:
            if not self.token:
                raise PhoneControlError("Session auth token unavailable")
            headers["Authorization"] = f"Bearer {self.token}"

        body: bytes | None = None
        if form is not None:
            body = urlencode({k: str(v) for k, v in form.items()}).encode()
            headers["Content-Type"] = "application/x-www-form-urlencoded"

        conn = http.client.HTTPConnection("127.0.0.1", self.forward_port, timeout=timeout)
        try:
            conn.request(method, "/" + path.lstrip("/"), body=body, headers=headers)
            response = conn.getresponse()
            data = response.read()
            if response.status < 200 or response.status >= 300:
                raise PhoneControlError(
                    f"HTTP {response.status} {response.reason}: {data[:300]!r}"
                )
            ctype = response.getheader("Content-Type", "")
            if "json" in ctype:
                return json.loads(data.decode("utf-8"))
            return data
        finally:
            conn.close()

    def _get_with_retry(
        self,
        path: str,
        *,
        timeout: float,
        attempts: int = 2,
        retry_delay: float = 0.03,
    ) -> Any:
        last_error: Exception | None = None
        for attempt in range(attempts):
            try:
                return unwrap_api(
                    self._request("GET", path, timeout=timeout)
                )
            except (OSError, TimeoutError, PhoneControlError) as exc:
                last_error = exc
                if attempt + 1 < attempts:
                    time.sleep(retry_delay)
        raise PhoneControlError(
            f"GET {path!r} failed after {attempts} attempts: {last_error}"
        )

    def state(self) -> dict[str, Any]:
        return self._get_with_retry("state", timeout=1.2)

    def phone_state(self) -> dict[str, Any]:
        return self._get_with_retry("phone_state", timeout=0.8)

    def tree(self) -> Any:
        return self._get_with_retry("a11y_tree", timeout=1.0)

    def tap(self, x: int, y: int) -> Any:
        return self._request("POST", "tap", form={"x": x, "y": y})

    def swipe(
        self,
        start_x: int,
        start_y: int,
        end_x: int,
        end_y: int,
        duration_ms: int = 300,
    ) -> Any:
        return self._request(
            "POST",
            "swipe",
            form={
                "startX": start_x,
                "startY": start_y,
                "endX": end_x,
                "endY": end_y,
                "duration": duration_ms,
            },
        )

    def input_text(self, text: str, *, clear: bool = True) -> Any:
        encoded = base64.b64encode(text.encode("utf-8")).decode("ascii")
        return self._request(
            "POST",
            "keyboard/input",
            form={"base64_text": encoded, "clear": clear},
        )

    def launch_app(self, package: str, activity: str | None = None) -> Any:
        form: dict[str, Any] = {"package": package}
        if activity:
            form["activity"] = activity
        return self._request("POST", "app", form=form)

    def wait_for_package(
        self,
        package: str,
        *,
        timeout: float = 2.0,
        poll_interval: float = 0.02,
    ) -> dict[str, Any]:
        deadline = time.monotonic() + timeout
        last: dict[str, Any] = {}
        while time.monotonic() < deadline:
            try:
                state = self.phone_state()
            except (OSError, TimeoutError, PhoneControlError):
                time.sleep(poll_interval)
                continue
            if isinstance(state, dict):
                last = state
                if state.get("packageName") == package or state.get("package") == package:
                    return state
            time.sleep(poll_interval)
        raise PhoneControlError(
            f"Timed out waiting for package {package!r}; last state={last!r}"
        )

    def launch_app_verified(
        self,
        package: str,
        activity: str | None = None,
        *,
        timeout: float = 2.0,
    ) -> dict[str, Any]:
        response = self.launch_app(package, activity)
        state = self.wait_for_package(package, timeout=timeout)
        return {"response": response, "phone_state": state}

    def screenshot_png(self, *, timeout: float = 4.0) -> bytes:
        cp = subprocess.run(
            [self.adb, "exec-out", "screencap", "-p"],
            check=False,
            capture_output=True,
            timeout=timeout,
        )
        if cp.returncode != 0:
            stderr = cp.stderr.decode("utf-8", errors="replace").strip()
            raise PhoneControlError(f"ADB screenshot failed ({cp.returncode}): {stderr}")
        data = cp.stdout
        if not data.startswith(b"\x89PNG\r\n\x1a\n"):
            raise PhoneControlError(
                f"ADB screenshot returned invalid PNG data ({len(data)} bytes)"
            )
        return data

    def global_action(self, action: int) -> Any:
        return self._request("POST", "global", form={"action": action})

    def tap_text(self, text: str) -> dict[str, Any]:
        state = self.state()
        nodes = list(flatten_nodes(state.get("a11y_tree", [])))
        matches = [n for n in nodes if n.get("text") == text]
        if not matches:
            raise PhoneControlError(f"Target text not found: {text!r}")
        node = matches[0]
        x1, y1, x2, y2 = parse_bounds(str(node.get("bounds", "")))
        response = self.tap((x1 + x2) // 2, (y1 + y2) // 2)
        return {"target": text, "bounds": node.get("bounds"), "response": response}

    def benchmark_reads(self, n: int = 30) -> dict[str, Any]:
        timings: list[Timing] = []
        for name, path in (
            ("ping", "ping"),
            ("phone_state", "phone_state"),
            ("a11y_tree", "a11y_tree"),
            ("state", "state"),
        ):
            values: list[float] = []
            self._request("GET", path, auth=(path != "ping"))
            for _ in range(n):
                t0 = time.perf_counter_ns()
                self._request("GET", path, auth=(path != "ping"))
                values.append((time.perf_counter_ns() - t0) / 1_000_000)
            timings.append(Timing(name, values))
        return {
            "n_each": n,
            "results": [item.summary() for item in timings],
        }


def sanitized_state_view(state: dict[str, Any], limit: int = 25) -> dict[str, Any]:
    phone = state.get("phone_state") or {}
    nodes = list(flatten_nodes(state.get("a11y_tree", [])))
    visible = []
    for node in nodes:
        text = str(node.get("text") or "").strip()
        if not text:
            continue
        visible.append({
            "index": node.get("index"),
            "class": node.get("className"),
            "text": text[:120],
            "bounds": node.get("bounds"),
        })
        if len(visible) >= limit:
            break
    return {
        "package": phone.get("packageName"),
        "app": phone.get("currentApp") or phone.get("appName"),
        "keyboard_visible": phone.get("keyboardVisible"),
        "elements": visible,
    }


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        try:
            sys.stdout.reconfigure(encoding="utf-8")
        except Exception:
            pass

    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("doctor")
    sub.add_parser("state")
    bench = sub.add_parser("benchmark")
    bench.add_argument("-n", type=int, default=20)
    tap = sub.add_parser("tap-text")
    tap.add_argument("text")
    args = parser.parse_args()

    session = MobilerunUsbSession()
    if args.command == "doctor":
        print(json.dumps(session.doctor(), indent=2))
        return 0

    with session:
        if args.command == "state":
            print(json.dumps(sanitized_state_view(session.state()), indent=2, ensure_ascii=False))
        elif args.command == "benchmark":
            print(json.dumps(session.benchmark_reads(args.n), indent=2))
        elif args.command == "tap-text":
            print(json.dumps(session.tap_text(args.text), indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
