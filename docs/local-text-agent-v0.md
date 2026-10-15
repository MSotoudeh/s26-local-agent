# Local Text Agent v0 — on-device deterministic Android execution

Date: 2026-10-13

## Goal and boundary

This is the first actual phone-local task runtime in `io.projectir.mobile`. A command is entered in the **Local Text Agent** panel of `MainActivity`; parsing, action dispatch, polling and postcondition verification run on Android in the application's main process. **Project IR Hub, Shizuku and ADB are not called by this task runtime.**

Project IR/USB ADB remain development and test-harness channels only. The tested APK uses the existing Project IR Android package and signature, not a new control app.

## Implementation

- `LocalCommandRouter.java`: exact allowlisted Persian/English command grammar with Arabic/Persian character normalization. Unknown and compound unsafe requests are rejected.
- `LocalTaskRunner.java`: a bounded single-task state machine (6-second timeout, 150 ms polling), package-guarded verification and no arbitrary shell/model output.
- `UiAccessibilityService.java`: bounded local state predicates and unique exact-text click within an expected package. Ambiguous or truncated trees cannot authorize a semantic click.
- `MainActivity.java`: input box, Run Locally button and persisted last-task status; stable resource IDs allow robust UI testing in portrait or landscape.
- `UiBridgeProvider.java`: optional test-harness `input_focused_base64` supports strict UTF-8 transport without shell-quoting errors, restricted to app UID or shell UID 2000 and input decoded length <=4096 bytes.
- `local_agent_ids.xml`: resource IDs for local input and run controls.

The task runtime does not use the BridgeProvider for itself; it directly calls its in-process Accessibility service. The Base64 method exists solely for reliable host-driven verification of the phone's own UI.

### Command allowlist

| Input sample | Router action | Postcondition |
| --- | --- | --- |
| `وضعیت گوشی` / `device status` | DEVICE_STATUS | Local Android API/model returned |
| `تنظیمات را باز کن` / `open settings` | OPEN_SETTINGS | Foreground package is Settings |
| `تنظیمات را باز کن و وارد Connections شو` | OPEN_CONNECTIONS | Strict Settings root precondition, unique semantic Connections click, Wi-Fi/Bluetooth/Connections visible on destination |
| `ماشین حساب را باز کن` | OPEN_CALCULATOR | Foreground package verified |
| `ساعت را باز کن` | OPEN_CLOCK | Foreground package verified |
| `نقشه را باز کن` | OPEN_MAPS | Foreground package verified |

Parser supports a limited number of exact Persian/English equivalents. It does **not** translate arbitrary language into arbitrary shell commands. It rejects unsupported input.

## Result types and persisted data

The phone stores only the last task's:
- normalized command enum;
- status (e.g. `RUNNING`, `PASS_VERIFIED`, `FAIL_VERIFICATION`, `FAIL_CAPABILITY`, `REJECTED`);
- safe detail/error class;
- elapsed milliseconds and updated timestamp.

It does not persist raw voice, entered command text, Accessibility trees, contacts, messages, screenshots or credentials.

A task may continue when the Activity backgrounds during `startActivity()`, as long as Android keeps the app process alive. This v0 does **not** persist a multi-step task across process death and is not a foreground task scheduler.

## Real-device validation

Target: Samsung SM-S948B / Android 17 API 37.

- Pure-Java parser: 15/15 assertions passed, including rejection of unsupported and shell-like input.
- Gradle `assembleDebug` and `lintDebug`: PASS on the real Android build source.
- Phone-local status command: `PASS_VERIFIED`, **1 ms** (one measured task).
- Phone-local `Settings -> Connections`: `PASS_VERIFIED`, **495 ms** in initial online UI run, later **349 ms** in strict-offline run.
- Three local app commands (verified foreground): Calculator **160 ms**, Clock **158 ms**, Maps **182 ms**.
- In strict-offline validation: Airplane Mode = on, Wi-Fi = off, mobile data = off, no IPv4 default route. Both DEVICE_STATUS and OPEN_CONNECTIONS succeeded and postconditions were verified. All initial network settings were restored afterward.

These timings are Android task times recorded by `LocalTaskRunner`, not the extra ADB shell/host measurement latency. They are individual samples, **not p50/p95 performance estimates**.

Strict offline tests retained a USB connection for invoking the test UI and guaranteed recovery. Actual command planning/action code did not use that USB link. Physical USB disconnect, repeated long-run failures and reboot recovery remain separate tests.

## Failed test harnesses and why

The first host harness used fixed screen coordinates; device orientation changed and the wrong view could be touched. UI controls now expose semantic resource IDs.

Further Windows PowerShell harness attempts encountered a reserved `$input` variable, Windows PowerShell 5.1 UTF-8 script/codepage differences, and lossy decoding of ADB UTF-8 output. The Android Router was occasionally falsely classified as failing because the harness supplied a corrupted command string or misread its UI text.

These were **harness defects**, not accepted product failures. A Python harness now reads ADB output as bytes, explicitly decodes UTF-8 and sends test command text as Base64 through the caller-restricted UI bridge. The subsequent strict-offline validation passed both commands.

Failed harness raw files are retained under the research repository's `benchmarks/m4/` for traceability, not active execution. The superseded PowerShell harness is removed from the runnable scripts directory.

## Security and limitations

- Only low-risk allowlisted actions; no arbitrary shell, no contact/message access, no banking/OTP flow, no external communication.
- No model, cloud or network listener in the Local Task Runner.
- Unknown commands are rejected.
- The Settings workflow uses exact English One UI labels and must be extended for alternate locale/versions; ambiguous selectors fail rather than tapping.
- Local task completion does not imply all app content or actions were tested; app launch means only its foreground package was verified.
- No push-to-talk, Persian ASR/TTS, local LLM, skill retrieval, scroll framework, or error recovery across process death yet.
- Runtime still relies on a user-enabled Accessibility service for UI tasks; this is independent of Shizuku but may need re-enabling after an APK replacement.
- No reboot test was performed.

## Follow-up

1. Add UI-accessible local task history and richer cancellation/confirmation.
2. Benchmark 100+ runs per simple command and 20+ per representative workflow.
3. Build bounded action IDs, stable page state fingerprints, scroll and text tasks under permission/risk policy.
4. Add offline Persian speech input and output only after ASR/TTS measurements.
5. Keep Shizuku/Project IR as optional privileged and management capabilities, never a dependency for the v0 local agent loop.

### Additional negative UI test

On the installed S26 application, submitting the shell-like string `open settings; rm -rf /` through the actual Local Text Agent UI resulted in a persisted `REJECTED / UNSUPPORTED` status. This is a single live negative test, not a fuzz test or proof of completeness. The research repository retains a sanitized JSON result.
