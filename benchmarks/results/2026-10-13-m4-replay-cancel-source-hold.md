# M4 — Explicit Action ID replay and active Cancel: source checkpoint

Date: 2026-10-13
Device: Samsung S26 Ultra
Scope: Android Local Text Agent, no reboot

## Initial audit

The feature worktree began clean at `47575d7`; research main was clean
at `4641396`; no durable Project IR run was listed. Project IR Hub initially
reported connected, though Shizuku server/permission/user-service telemetry
was negative. The native Accessibility ContentProvider remained responsive.
Shizuku was not modified because the local deterministic task path does not
depend on it.

## Added but not yet deployed

- Optional caller-controlled Action ID input; blank means auto-generated UUID.
- Explicit persisted `REJECTED_DUPLICATE` when an already-accepted Action ID
  is submitted again; no execution or journal change.
- A harmless cancellable `WAIT_FIVE_SECONDS` command in Persian and English.
  It uses an in-process delayed callback and changes no system settings.
- A device harness for three bounded checks: real duplicate-ID rejection,
  cancel during the timer and prevention of the delayed completion, and
  uninterrupted timer completion. It preserves prior foreground application
  when the test finishes.

## Results

| Check | Result |
| --- | --- |
| Pure JVM parser | 17/17 PASS |
| Pure JVM Action ID | 17/17 PASS |
| Project IR Python regression | 52/52 PASS |
| Android assembleDebug/lintDebug | PASS |
| Test-harness Python syntax | PASS |
| Live APK installation and UI experiments | **HOLD** |

The remote tool blocked the authorized installation command that included
APK certificate/signature verification and `adb install -r`. The tool response
was an OpenAI safety block; no workarounds, alternative remote tools, or
uninstall were attempted. No newly built APK was installed on the S26.

Source changes are independently useful but **not a successful end-to-end
Cancel/replay milestone**. The last live-verified Android build was the
earlier Reliable Executor v1 (`47575d7`).

## Remaining

Authorized device update; live replay and cancellation tests; 100-operation
endurance; UI scrolling with action/postcondition guards; physical USB
detachment. Reboot expressly deferred.
