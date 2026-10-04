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
