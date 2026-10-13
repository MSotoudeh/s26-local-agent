# M1 — Shizuku UserService recovery after controlled Binder interruption

Date: 2026-10-13
Device: Samsung SM-S948B (S26 Ultra)
Runtime: Project IR Mobile Agent + Shizuku API 13.1.5 + existing Project IR Hub

## Acceptance criterion

A UserService PID returning does not prove capability recovery. PASS requires, in this order:

1. Shizuku server remained running without restart.
2. Project IR app process remained running.
3. UserService became available with a new process.
4. The Hub heartbeat reported `shizuku_user_service_ready=true`.
5. A *new* direct `remote_exec(device_id="s26")` returned UID 2000 and exit code zero.
6. No app foreground transition was required during the fault/recovery test.

## Evidence

| Version | New UserService process | Process restoration | Hub Binder ready | Direct remote exec | Verdict |
| --- | --- | --- | --- | --- | --- |
| v1 | Yes | ~2.4 seconds | No | HTTP 502 / failed | FAIL |
| v2 trial 1 | Yes | ~11.1 seconds | Yes | UID 2000 / exit 0 | PASS |
| v2 trial 2 | Yes | ~6.6 seconds | Yes | UID 2000 / exit 0 | PASS |

Raw files:
- `../m1/20261013-shizuku-user-service-rebind.json` (v1)
- `../m1/20261013-shizuku-user-service-rebind-v2.json` (v2 trial 1)
- `../m1/20261013-shizuku-user-service-rebind-v2-repeat.json` (v2 trial 2)

The process recovery intervals come from sampling approximately every two seconds. Binder readiness is reported by a periodic heartbeat, so its observed delay may exceed the process restart interval. Do not interpret these as exact Binder reconnection latency. A two-trial success does not demonstrate long-run production reliability.

## Root cause and changes

The initial implementation could leave the app's Shizuku connection registered but unusable even after the UserService process was recreated. The previous status path only tracked the Shizuku server and permission.

The v2 implementation:
- explicitly retires the previous connection using `Shizuku.unbindUserService(..., false)` before rebinding;
- avoids an immediate rebind race inside the disconnect callback;
- times out pending binds after 12 seconds;
- runs a periodic readiness check even when the Hub connection is absent;
- sends `shizuku_user_service_ready` in heartbeat separately from server process/permission.

No change to the Project IR Hub deployment, Shizuku server installation, network mode, or app data was needed.

## Build and safe update

Java build and Android Lint PASS. Python suite 52/52 PASS after the first Java changes; v2 was Java-only and subsequently passed Android build/Lint.

The Ops remote user was SYSTEM, which had a different Android debug keystore. The initial APK install failed signature validation without changing the app. The original desktop debug keystore was confirmed to match the installed APK, and the re-signed update installed via `adb install -r` with data preserved. The AgentService is non-exported and could not be started externally; its Activity was opened briefly after installation, then the prior user application was restored.

## Decision

**GO for bounded single UserService process recovery** (2/2 direct-command verified trials). **HOLD** for reboot, prolonged resilience, multiple concurrent jobs during a failure and confidence-level acceptance. The phone was not rebooted.

## Additional safety

Both tests sent SIGTERM only to Project IR's own UserService process. The Shizuku server and other app processes were untouched, and user foreground state was preserved.
