# S26 M4.1 checkpoint: native offline multi-step Android plan adapter

Date: 2026-10-14
S26 documentation base: main @ a785b16
Android source repository: https://github.com/MSotoudeh/project-ir
Source branch: feat/s26-plan-adapter-20261014
Source commit: 0e650d3 (pushed and worktree clean)
Classification: SOURCE + BUILD + HOST SIMULATION GO; PHONE INTEGRATION HOLD

## Product objective and architecture decision

Target: a Persian-capable S26 Ultra assistant that can complete verified
multi-step tasks on the phone itself, without a live computer, Wi-Fi, root,
Shizuku, Hub or cloud service for the ordinary supported skills.

The mandatory path is ordinary Android/Accessibility APIs. Shizuku is an
optional enhancement for capabilities requiring shell privileges. Samsung
may terminate a wireless-ADB-started Shizuku server when Wi-Fi turns off;
unrestricted battery policy alone has not resolved the user's long-duration
case. No-root, post-reboot shell privilege is NOT a guaranteed capability.

An October 13 short strict offline experiment had 8/8 successes with USB
still connected. That does NOT meet prolonged/unplugged/post-reboot endurance.
On October 14, Android process inspection showed a Shizuku server PID, but
the live Project IR S26 heartbeat reported shizuku_running=false,
shizuku_user_service_ready=false, and one direct S26 remote_exec returned
HTTP 502. A live Binder connectivity problem remains. Do not confuse
process existence with usability; no server/app restarts were performed.

## Completed technical increment

The canonical Project IR Android branch feature/android-agent @ 44ba159
contained a pure-Java LocalPlanMachine that had never dispatched real
Android steps. Commit 0e650d3 adds:

- LocalAndroidPlanRuntime: opt-in, on-device main-looper scheduling and
  canonical R0 text-skill execution; no dependency on network or Shizuku.
- LocalPlanCoordinator: observe, write-ahead durable intent, guarded effect,
  durable result, machine acknowledgement and explicit later postcondition.
- LocalPlanJournal: sync-commit action identity and step outcomes; never
  automatically replay after crash/reboot/uncertain effect.
- Accessibility-only strict package/unique-node semantic clicking.
- New Activity run/cancel controls and mutual exclusion with legacy runner.
- 1,000-modelled-plan host tests and safety/failure injection.
- Documentation of limitations and live acceptance gates.

This is a **source integration**: the previously installed APK was NOT
replaced, so these features have NOT executed a real phone task.

## Verified tests

| Check | Result |
| --- | --- |
| Android assembleDebug + lintDebug | PASS |
| Project IR Python | 52/52 PASS (using matching worktree PYTHONPATH) |
| Existing LocalPlanMachineTest | 15,022 assertions / 1,000 simulations PASS |
| New LocalPlanCoordinatorTest | 9,015 assertions / 1,000 simulations PASS |
| Action identity / text router / local planning JVM suites | 17 / 17 / 48 PASS |
| Persian ASR benchmark evaluator unit tests | 9/9 PASS (pre-existing) |
| New APK installed on S26 | NO |
| Multi-step runtime actually executed on S26 | NOT TESTED |
| Long-run Wi-Fi off, USB detached, reboot | NOT TESTED |
| Persian audio decoded by a local model | NOT TESTED |

Seven non-blocking Java compiler warnings about missing annotation enums
were observed; Gradle assemble and lint still passed.

## Actual S26 isolated ART canary — 2026-10-14 update

Project IR source branch: `feat/s26-plan-adapter-20261014`
Canary evidence/code commit: `2aeeb6f` (pushed)
Canary package: `io.projectir.plan.canary`; not the main
`io.projectir.mobile` installation.

- Independent, zero-network-permission APK: Android build/lint PASS.
- On the actual S26 Android Runtime: 100/100 **simulated** multistep
  engine workflows PASS. Another 100/100 simulation run PASS.
- Android SharedPreferences durability: PREPARED state persisted,
  the canary app alone was force-stopped, recovery returned
  INTERRUPTED_UNKNOWN, and duplicate ID was rejected. PASS.
- Read-only actual Accessibility bridge: 20/20 usable complete
  snapshots, zero truncated; 47 nodes each; 19/19 adjacent unchanged.
  Raw screen content was neither persisted nor printed.
- Original Agent and Shizuku server process IDs remained unchanged
  during the bounded canary run. No Wi-Fi, Hub or phone reboot change.
- The project agent still reports no Shizuku Binder readiness although
  Android shows a Shizuku shell server process and Project IR's Shizuku
  permission granted. Root cause remains unverified.
- No actual multi-step UI click was made; original Agent APK remains
  unchanged. Real UI deployment and unplugged Wi-Fi-off endurance HOLD.

Detailed reproduction and test contracts are in
`project-ir/docs/android-plan-canary-device-20261014.md`.

## Safe next-install preflight staged without disturbing the Agent

At Project IR branch commit `d91dc26`, the prepared main Agent APK
has been signed with a locally available signing key whose certificate
**matches the installed package**. Android source build and lint passed.

- Candidate: `app-s26-matching-signer.apk` in the isolated Project IR
  worktree's Android build-output directory (not committed).
- SHA-256: `efb826ec1ff86b1478d3ee2839917cd78896659f806fd519e20db74699bb9ce2`.
- A read-only verified backup of the currently installed APK was also
  preserved under Ops local ProjectIR backups (not committed).
- Neither the production APK nor AgentService was replaced/restarted.
- **HOLD** installing the main APK until the user explicitly confirms
  that other concurrent Project IR chats can tolerate a brief disconnect.
- See `project-ir/docs/android-plan-agent-release-preflight-20261014.md`
  for signer checks, rollback boundaries and post-install acceptance.

## M5: actual offline Persian ASR / local LLM / Assistant-role milestone (2026-10-15)

Canonical implementation in Project IR:
- Repo: `MSotoudeh/project-ir`
- Branch: `feat/s26-voice-assistant-20261015`
- Commit: `614c934`
- Full evidence: `docs/S26_OFFLINE_FA_ASR_LOCAL_LLM_ASSISTANT_2026-10-15.md`

Delivered onto the **actual S26 Ultra**, not just simulated:
- Vosk Persian `small-fa-0.42` ASR model stored locally (about 97 MiB
  extracted); the packaged Persian WAV transcribed entirely offline, without
  microphone access or network use.
- One-sample quality check compared a 5.448-second bundled public WAV with
  its reference, obtaining WER 3.400, CER 1.864. That FAILS the accuracy gate;
  do not represent speech accuracy as production-ready.
- Push-to-talk microphone integration is implemented with Android runtime
  permission; actual spoken user tests remain pending. Recognized text is
  drafted for review, never auto-executed.
- MediaPipe model inference using an on-device Qwen2.5-0.5B-Instruct model,
  546,660,344 bytes, SHA-256 verified on the phone. Test query
  `Say hello in Persian` produced a nonempty Persian model answer.
  LLM outputs have **no** direct authority to execute phone actions.
- Android `ACTION_ASSIST` intent qualification implemented; explicit assist
  intent launched the app, the UI exposes an opt-in `ROLE_ASSISTANT` request.
  Default system Assistant remains Google until owner confirmation; the
  role picker/gesture acceptance is not yet validated.
- Android Gradle Build/Lint PASS with JDK 25, Python suite 52/52 PASS,
  existing JVM plan regressions PASS, and Hub plus Shizuku UserService
  remained connected after data-preserving APK replacement.
- New release is backed by a verified APK rollback copy on Ops.
  No model weights, APKs, tokens, phone speech, or other sensitive data
  were committed to Git.

This milestone is a **working offline prototype** and optional Assistant
role candidate, not a fully autonomous always-on personal assistant.

## Next gates

1. Preserve all active Project IR chats before any APK reinstall, AgentService
   restart, Shizuku restart or change to the Hub. Do not deploy source merely
   because the build passed.
2. During a controlled device canary, use the correct installed APK signature
   and data-preserving update; verify UI privileges and Binder state.
3. Prove a 2-step Settings/Connections task with repeated observations,
   no wrong-target clicks, task ID dedup, active Cancel, process interruption
   and restart with INTERRUPTED_UNKNOWN.
4. Explicitly test the residual Android Accessibility snapshot-to-click race,
   dynamic windows, screen lock, idle/battery state and Android background
   activity launch restrictions.
5. Compare Wi-Fi-started and USB-started Shizuku persistence over sustained
   off-Wi-Fi and physically unplugged operation. No bypass is assumed.
6. For voice, obtain a consented test audio corpus and a real local ASR model.
   The existing synthetic Persian text corpus and nine evaluator tests do
   not prove actual Persian speech recognition.
7. NPU/LLM and Assistant Role integration remain HOLD until independent
   latency/accuracy/security gates pass.

No changes were made to the installed phone agent, the live Hub, network
mode, Shizuku configuration, Wi-Fi, reboot state, or unrelated repositories.
