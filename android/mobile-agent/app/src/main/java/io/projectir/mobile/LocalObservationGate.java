package io.projectir.mobile;

/**
 * Monotonic single-looper settling gate for Accessibility observations.
 * Two identical full snapshots separated by a polling interval and a quiet
 * event window are required before the planner can select a live UI target.
 * No action and no retries happen inside this gate.
 */
public final class LocalObservationGate {
    public static final long MIN_QUIET_MS = 250L;
    private String lastStateId = "";
    private String lastPackage = "";
    private int stableCount;

    public LocalPlanMachine.Observation accept(
            LocalPlanMachine.Observation observed, long quietForMs) {
        if (observed == null || observed.truncated
                || observed.stateId.length() < 8 || observed.packageName.isEmpty()
                || quietForMs < MIN_QUIET_MS) {
            reset();
            return null;
        }
        if (lastStateId.equals(observed.stateId)
                && lastPackage.equals(observed.packageName)) {
            stableCount++;
        } else {
            lastStateId = observed.stateId;
            lastPackage = observed.packageName;
            stableCount = 1;
        }
        return stableCount >= 2 ? observed : null;
    }

    public void reset() {
        lastStateId = "";
        lastPackage = "";
        stableCount = 0;
    }
}
