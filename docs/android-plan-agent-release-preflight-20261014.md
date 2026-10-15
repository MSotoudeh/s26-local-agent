# Main Project IR Android Agent - install preflight only (2026-10-14)

Branch: `feat/s26-plan-adapter-20261014`
Source integration commit: `0e650d3`
Isolated device canary commit: `2aeeb6f`
Target installed package: `io.projectir.mobile`

## Staged candidate

A full Android `assembleDebug lintDebug` was successful earlier on Ops.
The original debug APK was signed by the SYSTEM-user keystore and did not
match the certificate of the currently installed `io.projectir.mobile`.

Without installing or altering the current phone package, the candidate APK
was re-signed using the existing user-owned Android Debug keystore on Ops,
then verified against the certificate of a temporary read-only pull of the
currently installed APK. Signature verification and equality: **PASS**.

Staged APK (build output only; ignored by Git, contains no credentials):

`C:\GitHub\project-ir-s26-plan-20261014\android\mobile-agent\app\build\outputs\apk\debug\app-s26-matching-signer.apk`

- Byte count: `3905123`
- SHA-256: `efb826ec1ff86b1478d3ee2839917cd78896659f806fd519e20db74699bb9ce2`
- Signature match to installed package: **PASS**.
- Installed APK updated: **NO**.
- Original Agent PID, Shizuku, Hub settings, Wi-Fi and phone reboot altered
  during signing: **NO**.
- Private keystore content and credentials are **not** in the repository.
  The signed artifact is also not committed.

## Critical go/no-go gates

**HOLD for installing or restarting `io.projectir.mobile`.** Other active
ChatGPT chats may be using its WebSocket and privileged UserService. Even an
`adb install -r` can terminate and replace the installed app process, and
the AgentService may require a deliberate UI foreground transition to
start after update. Do not assume zero downtime or automatic reconnect.

Before installing, explicitly ask the user to confirm that all concurrent
Project IR chats can tolerate a short interruption. During that window:

1. Re-check ADB authorization, APK SHA-256, Android manifest package and
   installed-vs-candidate signing certificate; never uninstall the old app
   or clear its data.
2. Inspect existing app foreground state, permissions, configuration,
   and Hub connectivity without disclosing tokens.
3. Install only the preverified signed APK with `adb install -r`. Do not
   restart the shared Hub or Shizuku server.
4. Restore the agent foreground service as safely as Android permits;
   do not infer success from ADB's `Success` output alone.
5. Require a new S26 Hub heartbeat and a live on-device Accessibility
   canary with explicit R0 Settings/Connections postconditions.
6. Check action ID deduplication, Cancel, state change after selection,
   journal recovery, and absence of wrong-target clicks. Record results.
7. The observed Shizuku Binder mismatch is a separate diagnostic; a
   server PID or granted permission does not demonstrate binder usability.
8. Reboot, airplane mode, extended Wi-Fi-off and physical USB disconnect
   tests remain deferred until a controlled acceptance window.

The independent `io.projectir.plan.canary` test app is already installed
and demonstrated on-device ART simulation and durable recovery. It has not
been used to mutate any real UI.

## Rollback principle

Preserve a copy of the **currently installed** matching-signature APK and
package data before main-agent update; if connectivity fails, restore that
APK through data-preserving install, not uninstall/reset. Never proceed
with a rollback if the artifact signer or installed package is ambiguous.
A verified, read-only backup of the original installed APK has now been
preserved on Ops at:

`C:\Users\mmsot\AppData\Local\ProjectIR\backups\s26-agent\20261014-before-local-plan-update.apk`

- Size: `3925603` bytes.
- SHA-256: `2f234cf131452ab2813f570dbcc6ecd2a1dac5e7c4f3de7c48feda4b3bc58b34`.
- Android APK signature verification: **PASS**.
- Backed-up artifact is not committed, uploaded or distributed.
- This is an APK-only rollback artifact, **not** a backup of private app
  data or the Android Keystore (which should remain untouched).
- Recheck certificate and APK checksum at install/rollback time.
