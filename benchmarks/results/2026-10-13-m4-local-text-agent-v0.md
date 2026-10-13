# M4 — Local Text Agent v0: Android-local execution evidence

**Date:** 2026-10-13
**Phone:** Samsung SM-S948B (Android API 37)
**Milestone:** an executable deterministic agent on the handset, not a remote shell macro.

## Scope

The user enters Persian/English text into the Project IR Mobile Agent's **Local Text Agent** input and clicks **Run locally — no Hub**. The app's own Java `LocalCommandRouter` and `LocalTaskRunner` then execute allowlisted Android Intents/Accessibility actions and verify the postcondition. No ChatGPT, Project IR Hub, ADB or Shizuku calls are made by the local runtime.

## Results (single trials, not statistical quantiles)

| Workflow | Runtime duration | Verified result |
| --- | ---: | --- |
| Local device status | 1 ms | PASS_VERIFIED |
| Settings -> Connections (online) | 495 ms | PASS_VERIFIED; Wi-Fi/Bluetooth present |
| Local device status (strict offline) | 1 ms | PASS_VERIFIED |
| Settings -> Connections (strict offline) | 349 ms | PASS_VERIFIED; destination verified |
| Open Calculator | 160 ms | PASS_VERIFIED; expected package foreground |
| Open Samsung Clock | 158 ms | PASS_VERIFIED; expected package foreground |
| Open Google Maps | 182 ms | PASS_VERIFIED; expected package foreground |

The Android app, not the host script, measured its own elapsed task duration. The Host/ADB roundtrip overhead is excluded.

### Strict network isolation

During the strict-offline test:
- Airplane mode = 1
- Wi-Fi setting = 0
- mobile-data setting = 0
- no default IPv4 route
- both commands passed
- all radio settings restored exactly after the test

USB stayed attached exclusively for invoking the UI test and for safe rollback. **The results do not yet prove no-USB endurance**, but they establish that the local Task Runner can make decisions and execute verified actions while the Hub is unreachable.

### Regression/safety

- Java offline command parser: **15/15 tests PASS**
- Android APK `assembleDebug` + `lintDebug`: **PASS**
- Unsupported commands including a shell-like payload: **REJECTED in unit tests**
- No arbitrary shell or network calls in the local runtime.
- Raw text of user commands and screen content not stored in task telemetry.

### Host harness corrections

1. The fixed-coordinate PowerShell harness failed when the phone changed orientation.
2. It then surfaced reserved PowerShell `$input` and code-page/UTF-8 issues.
3. Stable Android UI resource IDs were added and a Base64 UTF-8 transport used for the shell-only test helper.
4. A Python harness using byte-oriented ADB subprocess output performed the successful strict-offline run.

These negative runs are retained as experimental evidence, not production Android failures.

Raw successful results:
- `../m4/20261013-local-text-v10-offline.json`
- `../m4/20261013-local-app-launch-smoke.json`

Superseded PowerShell harnesses were retired. Their partial result JSON files are historical diagnostic artifacts only.

## Classification

**M4 / first local deterministic text agent: GO (limited v0).**

**Full agent/product acceptance: HOLD** until more applications, repeated stress, scroll/input, side-effect contracts, no-USB endurance, background/process restart, voice and local intelligence are validated.

No reboot was attempted.

### On-device negative test (additional verification)

An unsupported shell-like string (`open settings; rm -rf /`) was entered into the real Android Local Text Agent input and submitted through the **Run locally** button. The device stored `REJECTED` with enum `UNSUPPORTED`; no allowlisted action was selected. Result: **PASS for this single negative case**, distinct from the 15 parser-unit assertions. Evidence: `../m4/20261013-unsupported-command-rejected.json`.
