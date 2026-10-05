# Architecture

## 1. Product boundary

The runtime should accept voice, text, or a hardware/UI trigger and choose the cheapest reliable execution path.

```text
User
  |
Activation
  |
Local speech / text normalization
  |
Command Router
  |-------------------------------|
  |               |               |
Fast Path      Agent Path      Fallback
native         a11y/skills      local/cloud
  |               |               |
  +---------------+---------------+
                  |
           Action Executor
                  |
              Verifier
                  |
               Android
```

## 2. Execution hierarchy

Preferred order:

1. Android public/native API
2. Shizuku-backed privileged operation
3. Known verified workflow / app skill
4. Accessibility tree + deterministic/semantic target matching
5. Tiny local structured model
6. Local LLM/VLM
7. Jev structured cloud decision, when online and advantageous
8. Large cloud LLM/VLM
9. Human confirmation/fallback

This is a policy, not a rigid sequence. Some tasks jump directly to a later stage when risk or ambiguity requires it.

## 3. Privilege hierarchy

```text
Public Android API
      ↓
AccessibilityService
      ↓
Shizuku / Binder privileged bridge
      ↓
USB ADB (bootstrap, diagnostics, remote lab)
      ↓
Root (optional future research only)
```

ADB must not be required for normal runtime operation.

## 4. Perception hierarchy

```text
Known state / native query
        ↓
Accessibility tree
        ↓
Cached UI fingerprint / workflow
        ↓
Semantic matching
        ↓
Screenshot
        ↓
Local VLM
        ↓
Cloud VLM
```

Screenshots are a fallback, not the default representation.

## 5. Decision architecture

### System-0
Pure deterministic logic:
- exact commands;
- regex/grammar;
- package lookup;
- known settings;
- risk checks;
- capability routing.

### System-1
Fast bounded decisions:
- embedding/semantic target selection;
- small classifier;
- FunctionGemma-class model;
- Jev when online and empirically faster/more accurate.

### System-2
Expensive reasoning:
- local 1B–4B model;
- cloud GPT/Claude/Gemini class models;
- VLM when the tree is insufficient.

## 6. Bounded action contract

The controller should expose valid actions:

```json
{
  "state_id": "abc123",
  "actions": [
    {"id": "A17", "kind": "click", "label": "Ali"},
    {"id": "A18", "kind": "click", "label": "Search"},
    {"id": "A19", "kind": "global", "label": "Back"}
  ]
}
```

The decision engine returns an action ID and confidence, not arbitrary coordinates.

Raw coordinate actions remain an escape hatch only when no structured target exists.

## 7. Action transaction model

Every mutating action should have:

- `action_id`
- risk class
- precondition
- executor
- timeout
- expected postcondition
- verifier
- retry policy
- idempotency semantics

This prevents duplicate actions when a timeout occurs after a successful side effect.

## 8. Risk classes

| Class | Example | Default policy |
|---|---|---|
| R0 read-only | read screen, open app | auto |
| R1 reversible local | volume, brightness | auto when confidence high |
| R2 external communication | send message | target verification + high confidence |
| R3 destructive/sensitive | delete, banking, security changes | explicit confirmation |

## 9. Connectivity modes

### Offline
No cloud dependency. Deterministic + local models only.

### Smart
Prefer local fast path. Escalate based on measured policy.

### Cloud-first
Useful as a benchmark/reference mode, not the desired default.

## 10. Transport separation

The assistant runtime and the remote testing control plane are separate.

```text
S26 Runtime
  ├─ Local assistant
  ├─ Accessibility
  ├─ Shizuku
  └─ Control bridge
        ├─ USB ADB
        ├─ local socket
        └─ optional outbound WSS
```

This lets an external agent test the phone even when the assistant brain is disabled.
