# Repository ownership, migration ledger, and operational cutover — 2026-10-15

## Decision

The owner chose `MSotoudeh/s26-local-agent` as the **sole maintained repository for all on-phone code**. The separate `MSotoudeh/project-ir` repository continues to own its generic Hub, remote execution, service infrastructure, multi-device coordination, remote authentication, and other non-phone tooling.

As of this migration, **new Android phone changes MUST be developed and released exclusively from `s26-local-agent/android/mobile-agent`**. The unchanged Android package `io.projectir.mobile` and signer are intentional to preserve the already-installed owner app, its Keystore-backed settings, Shizuku registration and default Android Assistant role. Renaming package/namespace is a separate explicit migration requiring Android signing and data plan.

## Import and Git history

- Existing destination base: `MSotoudeh/s26-local-agent`, branch `docs/m4-android-plan-bridge-20261014`, commit `23f0838`.
- Existing source: `MSotoudeh/project-ir`, branch `docs/s26-model-lab-benchmark-roadmap-20261015`, commit `b68d301`, parent on-phone voice/Assistant `205e08c`.
- Path-scoped history export made with `git subtree split --prefix=android/mobile-agent` and export SHA `2d4d9818205f15909cae642745b0888455b36d47`.
- Destination preserved Git ancestry of the 48 tracked Android source and Gradle project files by `git subtree add --prefix=android/mobile-agent`; import commit `d8aa376`.
- Additional phone-only artifacts were migrated to the destination: `android/model-lab/`, all `docs/S26_*`, Android plan and offline skill docs, `scripts/Install-S26Agent-Safe.ps1`, build/bootstrap and on-device canary scripts, `Audit-16k-ELF.py`, model-lab validator, and Persian ASR evaluation script. Their original source commit SHAs remain available in the source repository. This copying does **not** erase source Git history.
- All PSTN experiment assets, prior measurements, and preexisting `s26-local-agent` content were retained without replacing or deleting them.
- Git sources and phone APK do **not** embed model weights, private agent data, ADB owner device identifiers, tokens, login data, or production keystores.

## Repository boundaries and future code workflow

```text
MSotoudeh/s26-local-agent  (canonical ON-PHONE)
  android/mobile-agent/   Android app and tests (io.projectir.mobile)
  android/model-lab/      catalogs, runtime model metadata, fixture specs
  android/pstn-audio-lab/ call/audio research
  docs/                   mobile architecture / benchmarks / roadmap
  scripts/                S26 install, test, benchmark and audit

MSotoudeh/project-ir      (canonical OFF-PHONE)
  src/...                 Hub, remote runner/coordination and generic backend
  cloudflare/             shared deployments and transport
  deploy/                 infrastructure / backend tooling
  docs/...                cross-device and general transport architecture
```

Historical `project-ir` branches may contain older phone code for reproducibility; **they are frozen lineage, not a second maintained Android codebase**. Source cleanup/deprecation is a separate change to avoid abruptly breaking existing Project IR scripts or parallel worktrees. Do not force-rewrite either repository's history; do not delete source history. Future fixes must originate in `s26-local-agent`, with any backend changes done through a narrowly scoped coordinated PR to `project-ir`.

## Validation and rollout policy

1. Verify destination Git import count and signed Android app hash; run Android `assembleRelease lintRelease` using installed JDK25, Android SDK 37 and Gradle 9.6.
2. Re-run Java standalone planner/ASR/voice router regressions and the destination Python tests.
3. Validate model-lab manifests and their negative safety tests.
4. Confirm on-phone source is unchanged by this *repository-only* migration: still `io.projectir.mobile`, both models present, Accessibility and Assistant holder intact.
5. Any future APK upgrade: hash/signature match, read-only rollback APK backup, `adb install -r`, on-device app/voice/Accessibility smoke, no uninstall/clear/reboot.
6. For 16 KiB pages, retain the `libvosk.so` aligned 0.3.75 JNI and compressed legacy packaging. The new Git home alone changes no ELF or APK behavior.

## Model Lab milestones (from the transferred roadmap)

- **M0 migration**: Git history, app, model catalog, 30 fixture definitions, tests and docs migrated.
- **M1**: Implement `ModelStore`, `ModelSwitcher`, `LocalModelAdapter`, resource/permission gates, simple Model Lab screen; two tiny fake adapters for atomic-swap/cancel/rollback tests.
- **M2–M5**: Install real ASR/VAD/TTS/LLM/VLM candidates in batches, implementing each runtime and verifying on-device before promoting `installed_verified`. Never equate `downloaded` with `working`.
- **M6–M8**: conversational audio loop, screenshot opt-in, opt-in web search, heavy RAM/thermal benchmarks, scenario-specific default model presets.
- **Licensing:** do not silently download gated Gemma weights or distribute NC MMS voices; actual artifacts pinned by revision and hash. Heavy 9B/14B benchmarks stop on thermal/RAM gates.

**Current execution truth:** the previous Vosk and Qwen model weights remain installed on the phone; the model alternatives recorded as planned in `catalog.json` are not automatically available. Migrating Git does not count as installing models or validating new TTS/VLM.
