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
