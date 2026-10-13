# Decisions

This file records current architecture decisions. Revisit when experiments contradict them.

## D001 — Feasibility-first development

Status: accepted.

Every uncertain subsystem is benchmarked independently before integration. Long implementation work is not justified by architectural elegance alone.

## D002 — ADB is not normal runtime

Status: accepted.

USB ADB is a bootstrap, diagnostic and lab-control transport. The assistant must remain functional after USB disconnect.

## D003 — Shizuku is preferred privileged local bridge

Status: accepted provisionally.

User has already verified on this S26 Ultra that Shizuku requires Wi-Fi for initial bootstrap and then keeps working until reboot. M1 will quantify reliability/latency.

Alternative: public APIs + Accessibility; lab fallback: USB ADB.

## D004 — Accessibility tree precedes screenshot vision

Status: accepted.

Tree-based operation is cheaper, faster, more private and easier to verify. Vision is fallback.

## D005 — Models should choose bounded actions when possible

Status: accepted.

Controller generates the valid action set. Decision systems choose among actions. Raw coordinate generation remains fallback.

References to evaluate: Jev Mobile, Jev Android.

## D006 — Skills are retrieved, not globally prompted

Status: accepted.

Store many skills/workflows if useful, but retrieve only a small local candidate set based on intent/package/vendor/version.

## D007 — Samsung-specific workflows are allowed

Status: accepted conditionally.

They must demonstrate measurable reliability or latency benefit over generic control.

## D008 — NPU is preferred, not mandatory

Status: accepted.

Backend choice is empirical. If GPU/CPU is faster, more stable or thermally better for a model, use it.

## D009 — Persian is a first-class benchmark

Status: accepted.

Persian and Persian-English mixed voice commands must be measured on the user's speech, not inferred from English benchmarks.

## D010 — Smart fallback is evidence-driven

Status: accepted.

No generic "local then cloud" chain is assumed optimal. Routing uses measured task/backend data.

## D011 — Remote control plane is separate from assistant runtime

Status: accepted.

This allows external independent testing/recovery similar to RDC while keeping normal local operation independent.


## D012 — Direct Accessibility passes the latency gate

Status: accepted experimentally.

On the target S26 Ultra, Mobilerun over persistent HTTP via ADB forwarding produced:
- a11y tree p50 4.88 ms / p95 5.71 ms;
- combined state p50 5.92 ms / p95 7.36 ms.

The same app through repeated `adb shell content` calls took ~1.13 s because Android's ContentProvider CLI startup dominated the measurement.

Decision:
- direct Accessibility is viable as the primary perception layer;
- per-turn ContentProvider CLI is rejected;
- stock Mobilerun remains lab-only because its permission/feature surface is broader than needed;
- production work should use a stripped fork or minimal custom bridge.


## D013 — Screenshot stays out of the primary perception loop

Status: accepted experimentally.

Follow-up measurements on the target S26 Ultra confirmed that structured Accessibility state is the fast path while screenshot capture has materially worse latency and reliability:

- PhoneController Accessibility tree: p50 29.45 ms / p95 33.94 ms in the 2026-10-05 live follow-up;
- Mobilerun HTTP screenshot: p50 734.06 ms with 5/15 timeouts and ~3.0 s p95;
- raw ADB binary screenshot transfer: p50 1012.10 ms / p95 1020.88 ms.

Decision: use screenshots only for visual-only/inaccessible surfaces or diagnostics. Do not include screenshots in the normal per-turn loop.

## D014 — Separate base action latency from app-settle latency

Status: accepted experimentally.

A no-op-style Accessibility tap measured p50 26.12 ms / p95 29.65 ms with zero failures. Repeated app-foreground actions showed a multi-second tail and 2/20 failures.

Decision: do not attribute app-launch tails to the transport. Each heavier action class needs its own settle condition, timeout, retry/recovery policy, and verification predicate.

## D015 — Retire Mobilerun as a production dependency

Status: accepted, completed 2026-10-07.

Mobilerun proved that Accessibility actions and structured state can be low-latency, but repeated workflow reliability failed the acceptance gate. A 30-cycle selector/settle baseline reached only 60%, and event-sequence experiments degraded to 13.3% and 3.3%.

Decision: keep raw evidence and reference value; remove Mobilerun runtime packages, source/build temp artifacts, and active adapter code.

## D016 — Project IR Mobile Agent is the canonical remote phone control plane

Status: accepted.

Project IR already had an Android agent with an outbound Hub connection and Shizuku UserService. Extending that app avoids another daemon, credential surface, network listener, and lifecycle manager.

Decision: remote phone control runs through `device_id=s26` directly. Ops/ADB are bootstrap and recovery paths only.

## D017 — Native Accessibility semantics replace coordinate-first control

Status: accepted experimentally.

The Project IR bridge performs exact-text/resource semantic actions using Accessibility nodes and clickable ancestors. A Samsung Settings workflow passed 20/20 direct on-phone cycles.

Decision: structured semantic actions are the default UI actuator. Raw coordinates remain an escape hatch outside the bridge, not a normal model output.

## D018 — The UI bridge exposes no additional network server

Status: accepted.

The bridge uses a ContentProvider restricted in code to the app UID and shell UID 2000. Hub transport remains owned by the existing Project IR Mobile Agent.

Decision: no Mobilerun-style extra HTTP/WebSocket service for semantic UI control.

## D019 — Event sequence is a hint, not a correctness oracle

Status: accepted.

Mobilerun Lab event/quiet-window experiments did not improve reliability. Accessibility events are noisy, can be absent for idempotent actions, and do not prove that the expected postcondition holds.

Decision: correctness comes from explicit postcondition verification against structured state. Event signals may later accelerate settling but cannot replace verification.

## D020 — Distinguish local offline control from Hub reachability

Status: accepted experimentally, 2026-10-13.

Airplane Mode alone was insufficient to guarantee no network. A stricter test disabled Wi-Fi and mobile data and verified that no IPv4 default route existed. Native local provider and Shizuku processes survived 8/8 samples.

Decision: track offline local control and external Hub transport separately.

## D021 — Resolve OEM app launcher failures before blaming Accessibility

Status: accepted experimentally, 2026-10-13.

Samsung Clock initially failed to launch via an implicit launcher Intent. The resolved explicit launcher activity worked, and subsequent state snapshots passed 6/6.

Decision: classify launcher, tree, semantic-action and postcondition failures separately.

## D022 — Small positive smoke tests do not establish product readiness

Status: accepted, 2026-10-13.

M2 passed 24/24 snapshots across four apps and 10/10 Settings semantic workflows. This is an encouraging feasibility signal, not reliable statistical proof of greater than 98% success across apps and scenarios.

Decision: retain full M2 acceptance as HOLD until broader repeated and side-effect-safe tests pass.

## D023 — Reboot requires a proven recovery path

Status: accepted, 2026-10-13.

An unrooted device may not restore Shizuku automatically after restart and the unattended bootstrap mechanism is not yet verified.

Decision: do not deliberately reboot during remote automation until a tested recoverable procedure exists.

## D024 — Shizuku health requires Binder and execution verification

Status: accepted experimentally, 2026-10-13.

A terminated Shizuku UserService process was recreated in ~2.4 s without recovering the app's Binder. The Hub remained connected but direct shell execution failed. A new PID was insufficient for success.

Decision: distinguish Shizuku server, Shizuku permission, live UserService Binder and a verified shell command as separate health dimensions. A process-only test remains pending until the Hub reports ready and a direct command succeeds.

## D025 — Bounded rebind must retire the stale Shizuku connection

Status: accepted experimentally, 2026-10-13.

Version 13.1.5 of the installed Shizuku API supports `unbindUserService(..., false)`. Stale connection records are now unbound before retry, with a 12-second pending-bind expiry and a watchdog independent of Hub connectivity.

Two controlled UserService SIGTERM trials passed full recovery: UserService process reappeared in 11.1 s and 6.6 s, then the Hub reported Binder ready and direct shell execution as UID 2000 succeeded. No phone reboot or Shizuku server restart was performed.

Decision: retain minimal recovery logic; do not install a second phone-control app or require foreground interaction to recover a single lost UserService Binder.

## D026 — Android app updates must use the existing signing identity

Status: accepted, 2026-10-13.

A build from the SYSTEM account produced a different debug signature and Android rejected updating the existing app. The desktop account's original debug keystore matched the installed package. The APK was re-signed and safely updated while preserving data.

Decision: certificate-digest verification is a pre-install gate; never uninstall an authenticated phone agent to bypass signature mismatch. Keep keystores outside Git and use a process-local Gradle/JDK environment.

## D027 — Move the decision loop onto the phone before benchmarking large LLMs

Status: accepted experimentally, 2026-10-13.

Direct Project IR/Shizuku control proved actuation feasibility, but the phone was not autonomously deciding what to do. Building Local Text Agent v0 inside the existing Android package separates a real on-device execution loop from the optional Hub management channel.

Decision: local deterministic router + allowlisted Intents/Accessibility verifier is the first shipped execution path. Large LLM/NPU are optional later enhancements, not prerequisites.

## D028 — No arbitrary commands or silent unverified actions

Status: accepted, 2026-10-13.

The local parser accepts a short, exact Persian/English grammar and rejects unknown input, including shell-like payloads. Each supported launched-app command requires foreground package verification; Settings/Connections requires unique semantic node click and destination text verification.

Decision: extend the typed action vocabulary before introducing model-directed planning; risk and postcondition checks are mandatory.

## D029 — Test UI controls with resource IDs and binary-safe Unicode

Status: accepted, 2026-10-13.

A coordinate harness failed after orientation changes, and multiple PowerShell test harnesses corrupted Persian command input/output. Stable UI resource IDs and a caller-restricted Base64 UTF-8 input method enabled reproducible external testing. A Python byte-oriented harness finally passed strict offline conditions.

Decision: keep harness transport separately validated and retain unsuccessful diagnostic evidence without misclassifying it as an Android runtime failure.

## D030 — Limited M4 GO is not full autonomous-agent acceptance

Status: accepted, 2026-10-13.

The Android-local status and Connections commands passed with radios off and no default IPv4 route; Calculator, Clock and Maps also passed single foreground-verification trials. USB remained attached for test/restore.

Decision: call this an on-device **Local Text Agent v0** with a bounded offline gate, not a proven offline voice assistant or an independent multi-step planning system. Reboot remains deferred.
## D031 — Durable bounded identity before Android task dispatch

Status: implemented and boundedly validated, 2026-10-13.

Action identity and ACCEPTED status are synchronously persisted before the
local runner launches an Android activity. Duplicate caller IDs in the last
64 accepted actions are rejected. This prevents accidental immediate replay,
but not replay of evicted IDs after enough intervening actions.

## D032 — Cancellation is a task-state boundary, not an undo operation

Status: source implementation/build validated, active-cancel live test HOLD.

Cancel invalidates queued polling callbacks and marks the active task as
CANCELLED. A prior Intent launch or Accessibility action can remain in effect.
An old ACCEPTED or RUNNING state after process restart is explicitly marked
INTERRUPTED rather than interpreted as success. A physical active-task Cancel
trial remains an acceptance requirement.

## D033 — Keep host timeout noise separate from on-device reliability

Status: accepted, 2026-10-13.

A Settings/Connections regression batch stopped after 3/5 because an ADB
host call exceeded its 12-second timeout. The failing raw batch was preserved.
The 30-second host timeout rerun passed 5/5. Both are disclosed; neither
alone proves system reliability, and no silent test-data relabeling is allowed.

## D034 — Explicit replay and Cancel require live device proof

Status: source complete; device acceptance HOLD, 2026-10-13.

An optional explicit Action ID allows duplicate-request tests while normal
UI usage retains automatic UUIDs. A five-second benign local timer makes
active cancellation observable without using privileged operations. Unit
and build checks cannot substitute for a device-level Cancel/replay test.

Deployment was blocked by Project IR's safety check. Decision: preserve
the valid source/test harness and stop at a source-only checkpoint, without
attempting to circumvent the block. Continue no-reboot policy.
