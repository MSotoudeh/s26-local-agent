package io.projectir.mobile;

import java.util.HashSet;
import java.util.Set;

/** Fail-closed policy for independently authored or model-proposed plans. */
public final class LocalPlanPolicy {
    private LocalPlanPolicy() {}

    public static String rejection(LocalSkillPlan plan) {
        if (plan == null) return "NO_PLAN";
        if (plan.risk != LocalSkillPlan.Risk.R0_READ_ONLY) return "RISK_REQUIRES_CONFIRMATION";
        if (plan.skillId == null || !plan.skillId.matches("[a-z][a-z0-9._-]{2,95}")) {
            return "INVALID_SKILL_ID";
        }
        if (plan.steps.isEmpty() || plan.steps.size() > 8) return "UNBOUNDED_PLAN";
        Set<String> allow = new HashSet<>();
        allow.add("com.android.settings");
        allow.add("com.sec.android.app.popupcalculator");
        allow.add("com.sec.android.app.clockpackage");
        allow.add("com.google.android.apps.maps");
        int duration = 0;
        for (int i = 0; i < plan.steps.size(); i++) {
            LocalSkillPlan.Step step = plan.steps.get(i);
            if (step == null || step.kind == null) return "INVALID_STEP";
            if (step.timeoutMs < 100 || step.timeoutMs > 5000) return "UNBOUNDED_TIMEOUT";
            duration += step.timeoutMs;
            if (step.kind != LocalSkillPlan.StepKind.REPORT
                    && !allow.contains(step.expectedPackage)) return "PACKAGE_NOT_ALLOWED";
            if (step.kind == LocalSkillPlan.StepKind.CLICK_UNIQUE_TEXT
                    && (step.selector.isEmpty() || step.selector.length() > 80)) {
                return "INVALID_SELECTOR";
            }
            if (step.kind == LocalSkillPlan.StepKind.VERIFY_TEXTS
                    && (step.assertions.isEmpty() || step.assertions.size() > 6)) {
                return "MISSING_POSTCONDITION";
            }
            if (step.kind == LocalSkillPlan.StepKind.REPORT && i != plan.steps.size() - 1) {
                return "PREMATURE_REPORT";
            }
            for (String value : step.assertions) {
                if (value == null || value.trim().isEmpty() || value.length() > 80) {
                    return "INVALID_ASSERTION";
                }
            }
        }
        if (duration > 20000) return "UNBOUNDED_TOTAL_DURATION";
        if (plan.steps.get(plan.steps.size() - 1).kind != LocalSkillPlan.StepKind.REPORT) {
            return "MISSING_FINAL_REPORT";
        }
        // Do not trust an R0 label on an arbitrary, model-authored plan.
        // A plan must exactly match a canonical local registration.
        LocalSkillPlan canonical = LocalSkillRegistry.canonicalById(plan.skillId);
        if (canonical == null) return "UNREGISTERED_SKILL";
        if (canonical.risk != plan.risk || canonical.liveValidated != plan.liveValidated
                || canonical.steps.size() != plan.steps.size()) return "MUTATED_SKILL";
        for (int i = 0; i < canonical.steps.size(); i++) {
            LocalSkillPlan.Step x = canonical.steps.get(i);
            LocalSkillPlan.Step y = plan.steps.get(i);
            if (x.kind != y.kind
                    || !x.expectedPackage.equals(y.expectedPackage)
                    || !x.selector.equals(y.selector)
                    || x.timeoutMs != y.timeoutMs
                    || !x.assertions.equals(y.assertions)) return "MUTATED_SKILL";
        }
        return null;
    }

    /** The known skill must be physically validated before live execution. */
    public static boolean mayExecute(LocalSkillPlan plan) {
        return rejection(plan) == null && plan.liveValidated;
    }
}
