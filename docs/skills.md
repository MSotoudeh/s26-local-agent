# Skills and App-Specific Knowledge

## Principle

Do not create one large prompt containing hundreds of skills.

Use staged retrieval:
1. infer task domain cheaply;
2. filter by current package/device context;
3. load only the few relevant capabilities/workflows.

The number of stored skills should not materially affect latency.

## Three layers

### Capability
Stable low-level operation:
- launch app;
- get/set volume;
- read current package;
- click node;
- set text;
- scroll;
- query selected system setting.

### Workflow
App/vendor-specific path that is faster or more reliable than generic exploration.

Example:
```text
Samsung One UI
→ Battery
→ Background usage limits
```

### Learned route
A previously successful route tied to:
- package name;
- app version;
- UI fingerprint;
- locale/theme where relevant;
- last verified timestamp;
- success count;
- latency stats.

## When app-specific workflows are justified

Add one only when at least one is true:
- task is frequent;
- generic agent requires several expensive reasoning turns;
- UI is known to be brittle for generic matching;
- native/deep-link/system component offers a much faster route;
- reliability improvement is measurable.

## Initial high-value domains

### Samsung / One UI
Likely valuable because the target is fixed hardware/software:
- Settings;
- Device Care;
- battery/background controls;
- DeX/display-related settings;
- permissions and app settings;
- modes/routines if accessible;
- selected Samsung utilities.

Prefer direct component/deep-link/Shizuku paths over click-through navigation when stable.

### Telegram
Potential workflows:
- open search;
- locate chat;
- read visible latest message;
- compose;
- send with R2 confirmation/verification.

### Browser/YouTube
Start generic. Add workflows only after measurements show a clear win.

## Registry

Suggested metadata:

```yaml
id: samsung.settings.background_limits
package: com.android.settings
vendor: samsung
risk: R1
version_range: "*"
entry:
  preferred: deep_link
fallbacks:
  - accessibility
verify:
  type: ui_state
telemetry:
  success_rate: 0.0
  p50_ms: null
fingerprint:
  required: false
```

## Retrieval

Fast lookup keys:
- intent namespace;
- current package;
- vendor;
- required capability;
- app version.

Only top candidates are materialized into model context.

## Invalidation

A cached workflow is not trusted when:
- package version changed materially;
- UI fingerprint mismatch;
- repeated failures exceed threshold;
- expected selector is absent;
- Android/One UI major version changed.

Then:
1. disable cached route for the current attempt;
2. use generic agent;
3. if success is verified, record a new route candidate;
4. require repeated success before promotion.

## Latency rule

Skill retrieval itself should remain local and deterministic (Room/SQLite + indexed keys). If skill selection requires an LLM over the entire catalog, the design is wrong.
