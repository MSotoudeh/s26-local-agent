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
