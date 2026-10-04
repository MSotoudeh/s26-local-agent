# Security and Privacy

## Default posture

- Core assistant must be usable offline.
- Keep raw screen/audio/message data local unless a cloud backend is explicitly selected by policy.
- Do not commit personal content, credentials or tokens to the repository.
- Remote control is a separate, explicitly enabled capability.

## Sensitive surfaces

Treat as high-risk:
- banking and payment apps;
- password managers;
- authentication/OTP flows;
- Secure Folder;
- biometric/PIN dialogs;
- destructive settings;
- account deletion;
- external communication.

The project must not attempt to bypass Android secure-screen or biometric protections.

## Remote control plane

Requirements before remote WSS/cloud relay is enabled:
- TLS;
- strong per-device authentication;
- revocable credentials;
- explicit enable/disable state;
- tool allowlist;
- audit log;
- rate limits;
- no unauthenticated LAN listener;
- no arbitrary shell by default.

USB ADB remains the preferred first development transport because its trust boundary is simpler.

## Model providers

Cloud providers are adapters. Policy decides what may leave device.

A request should be classified before upload:
- public/non-sensitive;
- personal;
- sensitive;
- prohibited-from-cloud.

## Screen capture

Prefer Accessibility tree.

Screenshot/VLM escalation should:
- be explicit in trace;
- avoid secure screens;
- redact where practical;
- not persist screenshots by default.

## Logs

Store:
- timings;
- action IDs;
- package names where useful;
- error classes;
- anonymized task IDs.

Avoid:
- full message bodies;
- contact lists;
- API keys;
- auth cookies;
- banking values;
- raw microphone recordings unless a benchmark explicitly requires them.

## Confirmations

R3 actions always require explicit confirmation.
R2 actions require target verification and policy-specific confirmation behavior.

## Shizuku

Treat Shizuku access as privileged. The assistant should expose only a narrow set of reviewed operations rather than arbitrary shell execution.
