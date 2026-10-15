package io.projectir.mobile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure offline, fail-closed task-state machine. This is a reference engine
 * whose returned requests are DATA, never Android/Accessibility operations.
 *
 * An adapter must check package + state ID again immediately before applying
 * a request, then ACK once. No automatic retries of side effects.
 */
public final class LocalPlanMachine {
    public enum State { READY, RUNNING, WAITING_ACK, SUCCEEDED, FAILED, CANCELLED }
    public enum EventKind { DISPATCH, WAIT, FINISHED }

    /** Snapshot metadata comes from a separate observation adapter. */
    public static final class Observation {
        public final String stateId;
        public final String packageName;
        public final List<String> visibleLabels;
        public final List<LocalSemanticSelector.Node> nodes;
        public final boolean truncated;

        public Observation(String stateId, String packageName,
                           List<String> visibleLabels, List<LocalSemanticSelector.Node> nodes,
                           boolean truncated) {
            this.stateId = stateId == null ? "" : stateId;
            this.packageName = packageName == null ? "" : packageName;
            this.visibleLabels = visibleLabels == null ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(visibleLabels));
            this.nodes = nodes == null ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(nodes));
            this.truncated = truncated;
        }
    }

    /** A pending effect, not an executable command. */
    public static final class Request {
        public final String requestId;
        public final LocalSkillPlan.StepKind kind;
        public final String expectedPackage;
        public final String expectedStateId;
        public final int selectedNodeId;
        public final String selector;

        private Request(String requestId, LocalSkillPlan.Step step, String stateId, int nodeId) {
            this.requestId = requestId;
            this.kind = step.kind;
            this.expectedPackage = step.expectedPackage;
            this.expectedStateId = stateId;
            this.selectedNodeId = nodeId;
            this.selector = step.selector;
        }
    }

    public static final class Event {
        public final EventKind kind;
        public final State state;
        public final int stepIndex;
        public final String reason;
        public final Request request;

        private Event(EventKind kind, State state, int stepIndex, String reason, Request request) {
            this.kind = kind;
            this.state = state;
            this.stepIndex = stepIndex;
            this.reason = reason;
            this.request = request;
        }
    }

    private final LocalSkillPlan plan;
    private final String taskId;
    private State state = State.READY;
    private int stepIndex;
    private long stepStartedAt = -1;
    private long lastNow = -1;
    private Request outstanding;
    private String terminalReason = "";

    public LocalPlanMachine(LocalSkillPlan plan, String taskId) {
        if (!LocalActionIdentity.valid(taskId)) {
            throw new IllegalArgumentException("Invalid task ID");
        }
        if (!LocalPlanPolicy.mayExecute(plan)) {
            throw new IllegalArgumentException("Only canonical validated R0 plans allowed");
        }
        this.plan = plan;
        this.taskId = taskId;
    }

    public State state() { return state; }
    public String reason() { return terminalReason; }
    public int stepIndex() { return stepIndex; }
    public LocalSkillPlan.StepKind currentStepKind() {
        return stepIndex >= 0 && stepIndex < plan.steps.size()
                ? plan.steps.get(stepIndex).kind : null;
    }

    /** Safe to repeat: while waiting for ACK, it never emits another effect. */
    public Event advance(Observation observed, long nowMs) {
        if (isTerminal()) return event(EventKind.FINISHED, terminalReason, null);
        if (!monotonic(nowMs)) return fail("NON_MONOTONIC_CLOCK");
        if (state == State.WAITING_ACK) {
            if (expired(nowMs)) return fail("ACK_TIMEOUT_NO_RETRY");
            return event(EventKind.WAIT, "AWAITING_ACK", null);
        }
        if (state == State.READY) state = State.RUNNING;
        if (stepIndex >= plan.steps.size()) return fail("STEP_OVERFLOW");
        if (stepStartedAt < 0) stepStartedAt = nowMs;
        if (expired(nowMs)) return fail("STEP_TIMEOUT");

        LocalSkillPlan.Step step = plan.steps.get(stepIndex);
        switch (step.kind) {
            case LAUNCH:
                return dispatch(step, "", -1);
            case CLICK_UNIQUE_TEXT:
                if (observed == null) return event(EventKind.WAIT, "OBSERVATION_UNAVAILABLE", null);
                if (observed.stateId.length() < 8) return fail("INVALID_UI_STATE_ID");
                LocalSemanticSelector.Decision found = LocalSemanticSelector.decide(
                        observed.stateId, observed.stateId, step.expectedPackage,
                        observed.packageName, step.selector, observed.nodes, observed.truncated);
                if (!found.accepted) return fail("SELECTOR_" + found.reason);
                return dispatch(step, found.stateId, found.nodeId);
            case AWAIT_PACKAGE:
                if (observed != null && step.expectedPackage.equals(observed.packageName)) {
                    next(nowMs);
                    return event(EventKind.WAIT, "PACKAGE_VERIFIED", null);
                }
                return event(EventKind.WAIT, "WAIT_PACKAGE", null);
            case VERIFY_TEXTS:
                if (observed != null && step.expectedPackage.equals(observed.packageName)
                        && !observed.truncated && observed.visibleLabels.containsAll(step.assertions)) {
                    next(nowMs);
                    return event(EventKind.WAIT, "POSTCONDITION_VERIFIED", null);
                }
                return event(EventKind.WAIT, "WAIT_POSTCONDITION", null);
            case REPORT:
                // Canonical plan requires final report; earlier verification
                // steps have already advanced successfully.
                state = State.SUCCEEDED;
                terminalReason = "VERIFIED_CANONICAL_PLAN";
                return event(EventKind.FINISHED, terminalReason, null);
            default:
                return fail("UNKNOWN_STEP");
        }
    }

    /**
     * ACK means the adapter has applied the request after revalidating its
     * pre-dispatch state. It does NOT by itself assert the resulting UI state.
     */
    public boolean acknowledge(String requestId, String preDispatchStateId,
                               boolean effectApplied, long nowMs) {
        if (state != State.WAITING_ACK || outstanding == null || !monotonic(nowMs)) {
            return false;
        }
        if (!outstanding.requestId.equals(requestId)) return false;
        if (expired(nowMs)) {
            fail("ACK_TOO_LATE");
            return false;
        }
        if (!outstanding.expectedStateId.isEmpty()
                && !outstanding.expectedStateId.equals(preDispatchStateId)) {
            fail("STALE_STATE_AT_DISPATCH");
            return false;
        }
        if (!effectApplied) {
            fail("EFFECT_REJECTED_OR_UNCERTAIN");
            return false;
        }
        outstanding = null;
        state = State.RUNNING;
        next(nowMs);
        return true;
    }

    public boolean cancel() {
        if (isTerminal()) return false;
        outstanding = null;
        state = State.CANCELLED;
        terminalReason = "CANCELLED_NO_UNDO";
        return true;
    }

    private Event dispatch(LocalSkillPlan.Step step, String stateId, int nodeId) {
        outstanding = new Request(taskId + "_" + (stepIndex + 1),
                step, stateId, nodeId);
        state = State.WAITING_ACK;
        return event(EventKind.DISPATCH, "ACK_REQUIRED", outstanding);
    }

    private void next(long nowMs) {
        stepIndex++;
        stepStartedAt = nowMs;
    }

    private boolean monotonic(long nowMs) {
        if (nowMs < 0 || nowMs < lastNow) {
            fail("NON_MONOTONIC_CLOCK");
            return false;
        }
        lastNow = nowMs;
        return true;
    }

    private boolean expired(long nowMs) {
        return stepStartedAt >= 0
                && nowMs - stepStartedAt >= plan.steps.get(stepIndex).timeoutMs;
    }

    private boolean isTerminal() {
        return state == State.SUCCEEDED || state == State.FAILED || state == State.CANCELLED;
    }

    private Event fail(String reason) {
        outstanding = null;
        state = State.FAILED;
        terminalReason = reason;
        return event(EventKind.FINISHED, reason, null);
    }

    private Event event(EventKind kind, String reason, Request request) {
        return new Event(kind, state, stepIndex, reason, request);
    }
}
