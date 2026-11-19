# Security policy

## Reporting a vulnerability

Please report security issues privately through GitHub's
[private vulnerability reporting](https://github.com/MSotoudeh/s26-local-agent/security/advisories/new)
for this repository. Do not open a public issue for anything that could expose
a device, a Hub endpoint or user data. Expect an acknowledgement within a week.

## Secrets and personal data

- Secrets live only in a git-ignored `secrets/` folder or `.env` file, never in
  tracked files. The agent's Hub token is entered on the phone and stored
  encrypted with an Android Keystore key; it is never committed.
- Do not commit device serial numbers, phone numbers, IMEIs, screen contents,
  audio recordings, message text, contacts or signing keystores.
- Benchmark evidence under `benchmarks/` must be sanitized before commit
  (see [docs/security-privacy.md](docs/security-privacy.md) and
  [docs/experiment-protocol.md](docs/experiment-protocol.md)).

## Scope notes

The Android agent can run privileged shell commands through Shizuku and drive
the UI through an AccessibilityService. Changes to `UiBridgeProvider`,
`ShellBridge`, `LocalPlanPolicy` or the command allowlist are security
sensitive and need a test that demonstrates the fail-closed behavior.
