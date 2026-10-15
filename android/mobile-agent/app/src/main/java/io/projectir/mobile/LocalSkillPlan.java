package io.projectir.mobile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable, capability-bounded plan: data only, never executable shell code. */
public final class LocalSkillPlan {
    public enum Risk { R0_READ_ONLY, R1_LOCAL, R2_EXTERNAL, R3_SENSITIVE }
    public enum StepKind { LAUNCH, AWAIT_PACKAGE, CLICK_UNIQUE_TEXT, VERIFY_TEXTS, REPORT }

    public static final class Step {
        public final StepKind kind;
        public final String expectedPackage;
        public final String selector;
        public final List<String> assertions;
        public final int timeoutMs;

        public Step(StepKind kind, String expectedPackage, String selector,
                    int timeoutMs, String... assertions) {
            this.kind = kind;
            this.expectedPackage = expectedPackage;
            this.selector = selector == null ? "" : selector;
            this.timeoutMs = timeoutMs;
            List<String> safe = new ArrayList<>();
            if (assertions != null) Collections.addAll(safe, assertions);
            this.assertions = Collections.unmodifiableList(safe);
        }

        public String summary() {
            switch (kind) {
                case LAUNCH: return "Launch " + expectedPackage;
                case AWAIT_PACKAGE: return "Verify foreground package " + expectedPackage;
                case CLICK_UNIQUE_TEXT: return "Click unique visible label " + selector;
                case VERIFY_TEXTS: return "Verify labels " + String.join(", ", assertions);
                case REPORT: return "Report verified outcome";
                default: throw new IllegalStateException("Unknown step");
            }
        }
    }

    public final String skillId;
    public final Risk risk;
    public final boolean liveValidated;
    public final String reason;
    public final List<Step> steps;

    public LocalSkillPlan(String skillId, Risk risk, boolean liveValidated,
                          String reason, List<Step> steps) {
        this.skillId = skillId;
        this.risk = risk;
        this.liveValidated = liveValidated;
        this.reason = reason;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
    }

    public String preview() {
        StringBuilder out = new StringBuilder();
        out.append("LOCAL SKILL: ").append(skillId).append("\n");
        out.append("RISK: ").append(risk).append("\n");
        out.append("EXECUTION: ").append(liveValidated
                ? "Allowlisted skill; preview does not execute"
                : "HOLD — UI path not yet live validated").append("\n");
        for (int i = 0; i < steps.size(); i++) {
            out.append(i + 1).append(". ").append(steps.get(i).summary()).append("\n");
        }
        return out.toString();
    }
}
