# ADR 0001: Feasibility-first architecture

Status: Accepted

## Context

The product is useful only if control feels fast, reliable and natural on the actual phone. Several key technologies—Persian ASR, NPU inference, UI agents and smart fallback—have uncertain real-world quality.

## Decision

Build experiments before product integration.

Every uncertain subsystem receives:
1. raw prototype;
2. benchmark dataset;
3. acceptance threshold;
4. GO/HOLD/KILL decision.

## Consequences

Positive:
- prevents sunk-cost architecture;
- enables aggressive replacement of disappointing components;
- produces evidence for smart routing.

Negative:
- early repo may look less like a finished app;
- some prototype code will be discarded intentionally.
