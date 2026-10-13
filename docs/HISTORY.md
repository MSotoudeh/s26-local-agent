# Experiment and Decision History

This document is the canonical narrative of how the S26 Local Agent control-plane architecture evolved. It records successful paths, failed hypotheses, why decisions changed, and which artifacts were retired.

## 2026-10-04 — Repository bootstrap and feasibility contract

The project began with a deliberate feasibility-first rule: uncertain primitives are tested in isolation before they become architecture.

The initial repository defined:
- low latency as a product requirement, not a later optimization;
- ADB as bootstrap/debug only, not normal runtime;
- Shizuku as the preferred privileged local bridge between reboots;
- Accessibility hierarchy before screenshot/vision;
- deterministic and bounded actions before LLM reasoning;
- local/cloud components as replaceable adapters;
- GO / HOLD / KILL-REPLACE gates for every milestone.

M0-A established the Ops development environment. ADB was available at:
`C:\Tools\Android\platform-tools\adb.exe`.

## 2026-10-05 — S26 census and raw ADB baseline

The S26 Ultra was connected and measured on the real device.

Observed baseline:
- device: Samsung SM-S948B;
- Android 17 / API 37;
- architecture: arm64-v8a;
- SoC property: QTI SM8850;
- SELinux: Enforcing;
- Shizuku installed;
- Mobilerun Portal was already installed and its Accessibility service was available.

Raw ADB measurements showed an important split:
- a simple ADB shell round trip was on the order of tens of milliseconds;
- a simple input action was also tens of milliseconds;
- UIAutomator hierarchy dumps were around 2 seconds;
- raw PNG screenshot transfer was around 1 second.

Decision:
- ADB command transport itself was fast enough for a lab control plane.
- UIAutomator was rejected for the primary perception loop.
- screenshots were classified as fallback-only.

## 2026-10-05 — Mobilerun Portal as a lab actuator

Mobilerun Portal 0.7.25 was tested because it already exposed Android Accessibility state and actions.

### ContentProvider path

The ADB ContentProvider interface had roughly one second of fixed overhead even for trivial requests. It was useful for bootstrap/configuration, but not for an interactive control loop.

Decision: reject ContentProvider as the normal high-frequency Mobilerun transport.

### Local HTTP over USB ADB forward

The Portal HTTP server was exposed only through an ADB forward. This removed Wi-Fi dependence and produced much better latency.

Representative measurements included:
- `phone_state`: about 32 ms p50;
- `a11y_tree`: about 60 ms p50 / 70 ms p95 in an early run;
- a later PhoneController sample: tree about 29 ms p50 / 34 ms p95;
- semantic/no-op-style tap: about 26 ms p50 / 30 ms p95.

A real Settings workflow once measured approximately:
- initial structured state: 33.9 ms;
- tap request: 27.5 ms;
- tap to verified Connections page: 32.4 ms;
- Back to verified root: 86.7 ms.

This proved a key architectural point: structured Accessibility control can be fast enough to feel interactive.

### Screenshot measurements

Screenshot performance remained poor:
- Portal HTTP screenshot: about 734 ms p50 with 5/15 timeouts and about 3 s p95;
- raw ADB PNG: about 1.01 s p50/p95 in the repeated baseline.

Decision: keep screenshot and VLM perception out of the normal loop.

### Persian text input

Portal Accessibility text input successfully wrote and re-read the exact mixed string:

`سلام test 123`

The successful request took about 40.2 ms and did not require switching the active IME.

Decision: UTF-8/Persian text transport is feasible through Accessibility.

### App-launch tail

Repeatedly foregrounding an app showed a very different distribution from primitive actions:
- action p50 around 32 ms;
- action p95 around 1.53 s;
- verified end-to-end p95 around 4.54 s;
- 2/20 failures.

Decision: never infer action-transport quality from app launch/settle latency. Heavy action classes need their own verifier and recovery policy.

## 2026-10-06 — Long-run Mobilerun reliability failure

Fast single operations were not enough. Repeated semantic workflows were stress-tested.

A 30-cycle Settings selector/settle test produced:
- 18/30 successful cycles;
- 60% success;
- fast tap latency remained intact;
- tree/settle paths developed multi-second tails.

Persistent HTTP connection reuse improved read latency and removed some connection churn, but it did not make the workflow production-reliable. One long 30-cycle run exceeded the execution budget.

Decision:
- low-level transport was not the main blocker;
- stock Portal's state/settle behavior was not acceptable as a production dependency.

## 2026-10-06 — Mobilerun Lab event-sequence experiment

Before abandoning Portal, one deliberately small source modification was tested.

A side-by-side debug package, `com.mobilerun.portal.lab`, was built from Portal 0.7.25. The stock Play Store package remained untouched.

The Lab variant added a cheap Accessibility event sequence/timestamp primitive so the host could:
1. issue an action;
2. wait for UI events to become quiet;
3. perform one tree traversal instead of repeatedly polling the full tree.

Supporting build work required:
- a portable JDK 17;
- Android Platform 34 for the upstream Portal project;
- fixing the Lab provider authority to follow the debug application ID.

### Result

The event hypothesis failed decisively.

Representative repeated results:
- event-driven test: 4/30 successful = 13.3%;
- clean-root hybrid event/tree/row-ancestor test: 1/30 successful = 3.3%.

In the 1/30 run:
- tap p50 was still about 17.7 ms;
- tap p95 about 19.9 ms;
- tree reads were usually tens of milliseconds;
- transition verification still failed.

Interpretation:
- adding event sequence/quiet-window logic did not solve semantic workflow reliability;
- further Portal-specific polling, timeout, and event tuning had poor expected value.

Decision: **KILL/REPLACE Mobilerun as the production control plane.**

The raw benchmark JSON files under `benchmarks/m05/` are retained as evidence. Portal runtime code is not retained in the current implementation tree.

## 2026-10-07 — Discovery/reconciliation of the existing Project IR Mobile Agent

Before building another Android daemon, existing Project IR work was audited.

A separate Project IR feature worktree already contained a native Android agent:

`C:\GitHub\project-ir-android-agent`

Package:
`io.projectir.mobile`

The app already provided:
- outbound connection to the Project IR Hub;
- Android device identity `s26`;
- encrypted bootstrap token storage;
- foreground service/autostart;
- Shizuku UserService shell execution.

After refreshing the app, Project IR could execute directly on the phone:

`ChatGPT -> Project IR Hub -> s26 -> Shizuku shell`

A direct probe returned shell UID 2000 and the correct S26/Android properties, with an inner execution around 103 ms.

This was the architectural turning point: a second remote-control app was unnecessary.

Decision: extend the existing Project IR Mobile Agent with a minimal semantic UI bridge rather than fork Mobilerun or create another permanent application.

## 2026-10-07 — Native Project IR Accessibility bridge

The mobile agent was extended with:
- `UiAccessibilityService`;
- `UiBridgeProvider`;
- an Accessibility service configuration.

The bridge intentionally exposes a narrow surface:
- status;
- structured current UI state;
- exact-text semantic click;
- resource-ID semantic click;
- text input into the focused editable node;
- Android global Accessibility actions.

Security properties:
- no additional HTTP listener;
- no additional WebSocket server;
- ContentProvider calls are accepted only from the app's own UID or shell UID 2000;
- Shizuku remains the privileged execution layer.

### Build/lint work

The bridge initially failed Java compilation because Android's JSONObject API declares checked JSONException. JSON construction was wrapped with a small internal helper and the build passed.

Existing Android-agent lint debt was also fixed rather than baselined away:
- `Files.readString` replaced with a minSdk-compatible read;
- charset overloads incompatible with minSdk 30 replaced;
- heartbeat scheduling changed from fixed-rate to fixed-delay;
- malformed Windows `local.properties` path corrected.

Final combined `assembleDebug lintDebug` completed successfully.

### Tree serializer bug

The first custom tree serializer returned only two nodes. The cause was an attempted deduplication key based on bounds/text/class/resource. Samsung exposes several distinct container nodes with identical geometry; dedup skipped a parent before its children were enqueued.

Fix:
- remove heuristic deduplication;
- rely on the Accessibility tree being acyclic;
- retain the hard 500-node traversal cap.

After the fix, the Settings root produced a real tree with `nodeCount=119`, including Search, Connections, Connected devices, Notifications, and other rows.

This failure is retained as an important design lesson: UI-tree compaction must never destroy graph reachability.

## 2026-10-07 — Direct on-phone semantic reliability test

The final validation path did not use Ops, ADB, or Mobilerun for task execution.

Path:

`ChatGPT -> Project IR Hub -> device_id=s26 -> Shizuku shell -> Project IR Accessibility bridge`

The test repeatedly performed:
1. clean-start Samsung Settings;
2. semantic `click_text("Connections")`;
3. verify Wi-Fi and Bluetooth in the Project IR tree;
4. Accessibility Back;
5. verify Connections and Connected devices on the root.

To avoid confusing Hub request duration with device reliability, testing was split into four batches of five cycles.

Result:
- batch 1: 5/5;
- batch 2: 5/5;
- batch 3: 5/5;
- batch 4: 5/5;
- aggregate: **20/20 successful cycles**.

A transient Project IR backend 502 occurred between batches. Because each batch was clean-started and idempotent, it was treated as control-plane transport noise and the affected batch was rerun rather than counted as a phone failure.

### Persian input parity

The native bridge then opened Settings search, focused the Samsung search field, wrote:

`سلام test 123`

and re-read the exact text from the Project IR Accessibility tree.

Result: **PASS**.

This establishes parity for the critical structured primitives that previously justified Mobilerun.

## 2026-10-07 — Mobilerun retirement and dependency cleanup

After native bridge parity passed:

Removed from the S26:
- `com.mobilerun.portal`;
- `com.mobilerun.portal.lab`.

Accessibility configuration was explicitly reconciled so the remaining enabled services are:
- Bitwarden;
- KDE Connect;
- Project IR Mobile Agent.

Removed from Ops:
- `C:\Temp\mobilerun-portal-v0.7.25`;
- the temporary JDK ZIP/staging directory;
- Android Platform 34, which had been installed only to build upstream Mobilerun.

JDK 17 was **not** deleted because it became a real Project IR Mobile Agent build dependency. It was moved out of Temp into:

`C:\Users\mmsot\AppData\Local\ProjectIR\toolchains\jdk-17.0.20.1+1`

Shizuku was retained because it is a canonical runtime dependency.

## Current canonical control plane

```text
ChatGPT
  -> Project IR
  -> Project IR Hub
  -> S26 Mobile Agent (outbound connection)
       -> Shizuku UserService (UID 2000 privileged shell)
       -> native AccessibilityService
       -> shell/own-UID-only ContentProvider bridge
```

Ops + USB ADB remain bootstrap, development, recovery, and APK-install paths only.

## Current gate status

- M0 device/runtime census: GO.
- M0.5 independent remote phone control: GO for feasibility and canonical architecture.
- Mobilerun production path: KILL/REPLACE, completed.
- Native Project IR semantic UI bridge: GO for continued development after 20/20 simple Samsung Settings reliability and Persian input parity.
- M1 no-Wi-Fi/Shizuku endurance: still requires its dedicated repeated offline benchmark after a Shizuku bootstrap.
- screenshot/VLM primary perception: rejected; fallback only.
- ASR, NPU routing, local LLM, smart cloud fallback: intentionally not promoted until their own raw benchmarks pass.

## 2026-10-13 — Interrupted-run audit, validation, and publication checkpoint

A previously interrupted Git hygiene step was reconstructed from the actual state on Ops rather than assuming it completed. The working copy was intact. Only the newly selected Android feature worktree and the independent S26 research repository were in scope; the Project IR main branch and other concurrent worktrees were intentionally left untouched.

Important environment discovery: remote commands now execute as Windows SYSTEM whereas the Git repositories are owned by the desktop user. Rather than changing machine-wide Git trust or repository ownership, every relevant Git operation used an explicit one-command `safe.directory` setting.

Project IR source and proof:
- worktree: `C:\GitHub\project-ir-android-agent`;
- branch: `feature/android-agent`;
- final Android `assembleDebug + lintDebug`: PASS;
- existing Project IR Python suite: 52/52 PASS with the correct source worktree and project virtual environment;
- final Android APK installed with `adb install -r` preserving configuration;
- direct Hub-to-`s26` shell identity: UID 2000;
- direct semantic Settings click + postcondition: PASS;
- staged diff whitespace and common credential-pattern scans: PASS;
- Project IR feature commit: `cdcb29b3befdea352440e88cac8e3768f63a8d72`;
- remote: `MSotoudeh/project-ir`, `feature/android-agent`, pushed and verified against remote HEAD.

The initial Python test attempts failed during collection because they used the wrong interpreter or incomplete dependencies. The existing Project IR venv was repaired (pytest and working Windows dependencies); a subsequent full 52-test run succeeded. The failure was an environment/setup problem, not Android code regression.

For the independent `s26-local-agent` repository, retired runtime adapter code and test scripts were deleted from the current tree, while benchmark JSON artifacts were deliberately retained as decision evidence. All retained M0.5 JSON files parsed successfully; a scan of text sources found no common credential/token patterns. Documentation was reconciled so the native Project IR Mobile Agent, not Mobilerun, is the active control-plane implementation.

The next open work is M1 offline/Shizuku endurance and M2 multi-app Accessibility reliability; the 20/20 Settings proof is a feasibility gate, not a claim that all apps or every offline mode work. Shizuku recovery after reboot remains a known limitation on the unrooted phone.

## 2026-10-13 — Live Shizuku binding recovery after publication

A read-only Hub status check after Git publication reported the S26 agent connected but Shizuku unavailable (`shizuku_running=false`, UID -1, permission false). This was investigated rather than assuming that Shizuku itself had stopped.

On Ops, USB ADB still identified the S26. Android process inspection showed that `shizuku_server` **was already running**. No Shizuku reinstall or system permission reset was required.

Opening `io.projectir.mobile/.MainActivity` caused Project IR Mobile Agent to rebind its Shizuku UserService. The user service appeared as `io.projectir.mobile:project_ir_shell`, and a direct `remote_exec(device_id="s26")` call again returned UID 2000 plus a successful Accessibility provider status. The Hub status initially retained the previous false values, then refreshed to `connected=true`, `shizuku_running=true`, `shizuku_uid=2000` and `shizuku_permission=true` on the next telemetry update.

The phone package inventory continued to show only Project IR Mobile Agent and Shizuku, not Mobilerun. Enabled Accessibility services remained Bitwarden, KDE Connect and Project IR. The foreground UI was returned to ChatGPT after verification.

**Lesson / next hardening task:** distinguish Shizuku server process availability from the app's Binder binding state and from last-reported Hub telemetry. If the server is running but shell execution is unavailable, attempt an idempotent rebind and verify with a real direct command before prompting for a new Shizuku bootstrap. Hub status can temporarily lag behind the true recovered state.

## 2026-10-13 — M1 isolated offline operation and M2 four-app perception

Initial Airplane Mode maintained the native UI provider and Shizuku processes for 12/12 observations, but was deliberately not classified as strict offline because Wi-Fi can stay enabled in Airplane Mode. A stricter trial enforced Wi-Fi=0, mobile data=0, airplane=1 and no IPv4 default route. Local provider and process persistence passed 8/8 samples, with complete radio-settings restoration.

The first four-app M2 run passed Settings, Calculator and Maps but failed to launch Samsung Clock with an implicit launcher Intent. A separate inspection resolved Clock's explicit launcher activity. After correcting launch routing, the second run passed all 24/24 structured snapshots across four apps and 10/10 repeated Settings semantic click/verify/back cycles.

No device-screen contents were persisted. Live Hub, Shizuku, network restoration and ChatGPT foreground were verified afterward.

A read-only speech/NPU inventory was captured, but neither offline Persian ASR accuracy nor actual NPU model acceleration is proven yet.

Reboot was intentionally deferred without a validated unattended Shizuku restart procedure. This is a safety HOLD, not a passing reboot test.

Evidence and interpretation: benchmarks/results/2026-10-13-m1-m2.md and raw benchmarks/m1, benchmarks/m2 and benchmarks/m3.

## 2026-10-13 — Automatic Shizuku UserService Binder recovery (M1 hardening)

The Project IR phone agent initially had a lifecycle gap: `ShellBridge` detected a disconnected Shizuku UserService Binder, but did not reliably detach/rebind its stale service connection until the app Activity was reopened. A separate `shizuku_user_service_ready` heartbeat signal was added to distinguish the local UserService Binder from the Shizuku server process.

**Attempt 1 (failed acceptance):** a controlled SIGTERM was sent only to the Project IR shell UserService process. Its process restarted in about 2.4 seconds; the Shizuku server and app processes remained unchanged. The initial test incorrectly regarded process resurrection as recovery. The Hub still reported `shizuku_user_service_ready=false`, and direct `remote_exec(device_id="s26")` failed with 502. The raw evidence was explicitly reclassified `FAIL_END_TO_END_PROCESS_ONLY` rather than discarded.

**Decision/pivot:** lifecycle recovery must clear old Shizuku connection records, not only retry `bindUserService`. The installed Shizuku API 13.1.5 was inspected: it provides `unbindUserService(args, connection, remove)`. The agent now unbinds with `remove=false` before a subsequent rebind (so Shizuku/UserService is not deliberately destroyed), delays retry out of the disconnection callback, and resets a stuck pending bind after a bounded 12-second deadline. A scheduled health check runs every ten seconds independently of the Hub WebSocket.

**Attempt 2 (two separate successful end-to-end tests):** the same single UserService process was terminated without touching the Shizuku server or bringing Project IR to the foreground. In test 1, the process returned in about 11.1 seconds; in test 2, about 6.6 seconds. In both, the Hub later reported `shizuku_user_service_ready=true`; a direct Project IR command executed as UID 2000 and exited zero. Shizuku server and main app process identities remained stable. The original foreground app remained untouched.

Android `assembleDebug` and `lintDebug` passed. The Python suite passed 52/52 tests after the first lifecycle patch; the follow-on patch was Android-only and passed a full Android build/lint.

**Signing/toolchain issue:** Ops Project IR executes as Windows SYSTEM, which uses a different Android debug keystore and an initially empty Gradle cache. The first installation attempt was correctly rejected by Android as `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; no uninstall was attempted and app data was preserved. The existing user-profile debug keystore was compared by certificate digest and exactly matched the installed APK. The debug APK was re-signed with that existing keystore, verified, then installed with `adb install -r`. The build was run with the desktop user's existing Gradle cache and explicit JDK/SDK paths, without changing system-wide environment variables or publishing keys.

The app's AgentService is intentionally non-exported; a direct ADB `am start-foreground-service` request was rejected. After an APK update, MainActivity was briefly opened to resume its internal service and the previously focused application was immediately restored. This is an *update/bootstrap concern*, not the recovery mechanism demonstrated by the two controlled UserService process tests.

**Gate:** bounded UserService Binder automatic recovery: GO (2/2 end-to-end checks); reboot startup, extended unattended persistence, and disconnect under active jobs: HOLD. Do not treat `shizuku_server` process presence or a new UserService PID as sufficient evidence of ability to execute commands.

Raw evidence: `benchmarks/m1/20261013-shizuku-user-service-rebind.json` (failed v1) and `...-v2.json`, `...-v2-repeat.json` (verified successful v2).
