# Android mobile execution

## Goal

Make an Android phone a first-class Project IR execution target without requiring a desktop relay at runtime.

The phone initiates an authenticated outbound connection to the existing Project IR Hub and is addressed by a normal Project IR `device_id` such as `s26`.

Canonical runtime path:

```text
ChatGPT / Project IR client
  -> Project IR Hub
  -> Android Mobile Agent
       -> Shizuku UserService for privileged shell execution
       -> native AccessibilityService for semantic UI state/actions
```

Ops/USB ADB are bootstrap, APK-install, development and recovery paths only.

## Phase 1: direct Android execution

The first vertical slice intentionally reuses the existing `local_exec` agent contract. No Hub route or protocol fork is required.

The Android app:
- connects outbound to the Hub;
- advertises its Android device ID;
- accepts the execution tool supported by the Hub;
- delegates privileged shell commands to a Shizuku UserService;
- returns bounded structured results through the normal Project IR remote-execution path.

This allows:

`remote_exec(device_id="s26", ...)`

to execute directly on the phone rather than being relayed through Ops.

## Privilege model

The normal application process owns:
- outbound networking;
- configuration;
- reconnect logic;
- foreground-service lifecycle;
- native AccessibilityService;
- semantic UI bridge.

Privileged shell work is delegated to a Shizuku UserService over Binder.

Observed on the target S26:
- Shizuku server running;
- Shizuku permission granted to Project IR Mobile Agent;
- UserService execution UID: 2000 (shell).

Shizuku is a capability, not the Hub transport. If Shizuku stops, the app can still maintain its outbound Hub connection and should report degraded capability rather than pretending the device disappeared.

## Semantic Accessibility bridge

The app includes `UiAccessibilityService` and `UiBridgeProvider`.

The bridge deliberately exposes a small semantic surface.

Read:
- service/event status;
- structured current Accessibility tree.

Actions:
- click exact text using the target node or a clickable ancestor;
- click a resource ID;
- set text on the currently focused editable node;
- perform Android global Accessibility actions.

Tree records include:
- stable per-snapshot integer ID;
- parent ID;
- text;
- content description;
- resource ID;
- class name;
- package name;
- screen bounds;
- clickable/editable/focused/enabled/scrollable flags;
- Accessibility action bitmask.

Traversal is capped at 500 nodes.

### Security boundary

The provider is exported only because Shizuku shell UID 2000 must be able to call it from Project IR's remote shell path.

The provider enforces the caller in code and accepts only:
- its own application UID;
- shell UID 2000.

It does **not** add:
- another HTTP listener;
- another WebSocket server;
- LAN discovery;
- cloud relay credentials.

The existing Project IR Hub connection remains the only remote transport.

## Connectivity

The phone opens the Hub connection outbound.

Normal runtime does not require:
- an inbound port;
- a public IP;
- a VPN;
- a PC relay;
- ADB-over-network;
- Wi-Fi specifically.

Cellular Internet is sufficient for the Hub transport when Android allows the foreground service to run.

The local assistant architecture may later operate offline for local-only tasks; Hub connectivity and local assistant functionality are separate concerns.

## Secrets

The Hub agent token is never committed.

Initial provisioning uses a transient app-scoped bootstrap JSON file. The app:
1. imports the Hub URL, device ID and token;
2. encrypts the token with an Android Keystore AES-GCM key;
3. stores only encrypted configuration;
4. deletes the plaintext bootstrap file.

## Reboot boundary

The app registers for `BOOT_COMPLETED` and uses a foreground `specialUse` service to restore the Project IR transport.

On an unrooted phone, the Project IR app cannot independently restore Shizuku after a reboot. Until Shizuku is started again:
- Hub connectivity can return;
- native app capabilities can return;
- shell-privileged execution remains degraded.

This boundary is explicit and should be represented in device status.

## Build environment

The Android app targets API 37 and requires JDK 17 for the current Gradle/Android toolchain.

Canonical Ops toolchain:
- JDK: `%LOCALAPPDATA%\ProjectIR\toolchains\jdk-17.0.20.1+1`
- Android SDK: `%LOCALAPPDATA%\Android\Sdk`
- Android platform: API 37

Build and lint with:

```powershell
.\scripts\build-android-agent.ps1
```

The helper sets process-local `JAVA_HOME`, `ANDROID_HOME` and `ANDROID_SDK_ROOT`; it does not change machine-wide Java settings.

## Bootstrap

Install/configure over USB ADB:

```powershell
.\scripts\bootstrap-android-agent.ps1 -ApkPath .\android\mobile-agent\app\build\outputs\apk\debug\app-debug.apk
```

Bootstrap:
- installs/updates the APK;
- preserves existing Accessibility services and adds the Project IR service;
- stages the transient Hub bootstrap file;
- launches the app so the secret is imported and deleted;
- requests/uses existing Shizuku authorization.

## Verified S26 results — 2026-10-07

Target:
- Samsung SM-S948B;
- Android 17 / API 37.

### Direct Project IR execution

`remote_hub_status(device_id="s26")` reported the phone connected.

`remote_exec(device_id="s26", command="id")` executed on the phone through the public Hub and returned shell UID 2000 after Shizuku binding.

The successful execution path did not pass through the Ops agent.

### Semantic UI reliability

Samsung Settings repeated workflow:
1. clean-start Settings;
2. semantic `click_text("Connections")`;
3. read Project IR Accessibility tree and verify Wi-Fi + Bluetooth;
4. global Back;
5. verify Connections + Connected devices on root.

The test was split into four five-iteration batches to avoid conflating Hub request-duration limits with device reliability.

Result:
- batch 1: 5/5;
- batch 2: 5/5;
- batch 3: 5/5;
- batch 4: 5/5;
- aggregate: **20/20 PASS**.

### Persian text input

The bridge focused Samsung Settings search and wrote:

`سلام test 123`

The exact text was read back from the Project IR Accessibility tree.

Result: **PASS**.

### Repository validation

After the Android bridge and pre-existing minSdk lint fixes:

- `scripts/build-android-agent.ps1`: **PASS**;
- Android `assembleDebug`: **PASS**;
- Android `lintDebug`: **PASS**;
- Project IR Python suite using the canonical environment and this feature worktree's `src`: **52/52 PASS**;
- final installed APK direct semantic smoke through `device_id=s26`: **PASS**.

The first Python test attempts failed only because they were run from incomplete test environments (global Python lacked Project IR dependencies; the existing venv initially lacked pytest/working pywin32). After repairing the existing Project IR venv and running against the feature worktree source, the full suite passed.

### Tree serialization correction

The first serializer attempted heuristic node deduplication and returned only two nodes on Samsung Settings because distinct container nodes can share geometry and labels.

The dedup heuristic was removed. The Settings root then produced a real 119-node snapshot.

Rule: compaction must never destroy tree reachability; bounded traversal is safer than heuristic structural deduplication.

## Rejected alternative: Mobilerun Portal

Mobilerun Portal was used as a feasibility reference before the native bridge existed.

It demonstrated that Accessibility primitives can be fast, but repeated state/settle workflows failed the reliability gate. Event-sequence modifications also failed to improve reliability.

After the Project IR bridge achieved direct execution, 20/20 semantic reliability and Persian input parity:
- stock Mobilerun was uninstalled;
- the Lab variant was uninstalled;
- its temporary source/build tree and Android Platform 34 dependency were removed.

Mobilerun is not a Project IR runtime dependency.

## Current next capability layers

1. Dedicated no-Wi-Fi / mobile-data / offline-mode matrix after Shizuku bootstrap.
2. Explicit Shizuku lost/recovered state reporting and reboot recovery UX.
3. Typed Android status/snapshot tools in addition to raw `remote_exec`.
4. Bounded node/action IDs and assertions for multi-step agent execution.
5. Native Intent/package/settings adapters for operations that do not need Accessibility or Shizuku.
6. Scroll/list/input stress tests across Samsung apps, Telegram, browser and YouTube.
7. Screenshot fallback only for surfaces unavailable to Accessibility.
8. Durable phone-local run checkpoints so a network flap does not lose multi-step execution.
9. Gradually move operations to the least-privileged stable Android API that can perform them.

## Acceptance status

Phase 1 direct Android execution: **PASS**.

Native semantic bridge feasibility: **PASS / GO**.

Remaining production hardening is tracked separately; these results do not imply that every app or Android surface is already supported.

## 2026-10-13 — Live Shizuku binding recovery after publication

A read-only Hub status check after Git publication reported the S26 agent connected but Shizuku unavailable (`shizuku_running=false`, UID -1, permission false). This was investigated rather than assuming that Shizuku itself had stopped.

On Ops, USB ADB still identified the S26. Android process inspection showed that `shizuku_server` **was already running**. No Shizuku reinstall or system permission reset was required.

Opening `io.projectir.mobile/.MainActivity` caused Project IR Mobile Agent to rebind its Shizuku UserService. The user service appeared as `io.projectir.mobile:project_ir_shell`, and a direct `remote_exec(device_id="s26")` call again returned UID 2000 plus a successful Accessibility provider status. The Hub status initially retained the previous false values, then refreshed to `connected=true`, `shizuku_running=true`, `shizuku_uid=2000` and `shizuku_permission=true` on the next telemetry update.

The phone package inventory continued to show only Project IR Mobile Agent and Shizuku, not Mobilerun. Enabled Accessibility services remained Bitwarden, KDE Connect and Project IR. The foreground UI was returned to ChatGPT after verification.

**Lesson / next hardening task:** distinguish Shizuku server process availability from the app's Binder binding state and from last-reported Hub telemetry. If the server is running but shell execution is unavailable, attempt an idempotent rebind and verify with a real direct command before prompting for a new Shizuku bootstrap. Hub status can temporarily lag behind the true recovered state.

## 2026-10-13 — Shizuku Binder recovery hardening

The app previously relied on an Activity reopen or future command attempt to recover a lost UserService Binder. The Hub could report Shizuku running while command dispatch failed.

Now:
- `ShellBridge` distinguishes a living Shizuku server and permission from a living UserService Binder.
- A ten-second scheduler independently checks Binder readiness even when the Hub WebSocket is absent.
- A disconnect callback clears state; actual rebind is scheduled outside the callback to avoid racing Shizuku's teardown.
- On retry, an old service connection is explicitly released via Shizuku `unbindUserService(..., false)`; this does not intentionally destroy the daemon.
- A binding attempt without callback is eligible for retry after a 12-second deadline.
- Heartbeat reports `shizuku_user_service_ready` separately from `shizuku_running`.

### Controlled tests and decision

A targeted SIGTERM was sent only to the Project IR shell UserService. The first implementation recreated its process (~2.4 s) but did not restore Binder readiness or working direct execution: **FAIL**. After bounded stale unbind/rebind handling, two independent trials returned to live Hub Binder readiness and working `remote_exec(device_id="s26")` as UID 2000. UserService process resurrection took ~11.1 s and ~6.6 s, respectively; server/app processes remained unchanged and the foreground app was not touched during either recovery test. These were bounded samples, not a reboot or long-run test.

Android `assembleDebug` and `lintDebug` succeeded. The Project IR Python suite returned 52/52 PASS after the initial changes; the later Java-only correction also passed full Android build/lint.

### APK signing and Windows SYSTEM environment

The remote execution service on Ops may run as SYSTEM. Its default `%LOCALAPPDATA%` and Android debug keystore differ from those of the desktop user. Build with the intended Android SDK/JDK and Gradle cache set explicitly for the process. Before `adb install -r`, compare the APK signer certificate digest against the installed app's signer. If a debug build used a different keystore, **do not uninstall the phone agent**: sign the APK with the existing matching debug keystore held outside Git, validate the APK signature, and then update it. No signing keys, passwords or credentials should be committed.

The current non-exported AgentService cannot be started from ADB shell. For a controlled APK update, MainActivity may be launched to resume the internal service; return the user's foreground app afterward. The Binder recovery tests above do not depend on this UI workaround.

Current result: **GO for two bounded single-UserService Binder interruptions**. Production/reboot resilience remains **HOLD** until a verified post-boot Shizuku start path and longer multi-action tests exist.
