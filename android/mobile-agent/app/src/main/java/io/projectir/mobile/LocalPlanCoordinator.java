package io.projectir.mobile;

/**
 * Serial orchestrator: observe -> request -> durable intent -> guarded effect ->
 * durable result -> machine ACK -> observed postcondition.
 * Must be called from one executor/looper by its Android owner.
 */
public final class LocalPlanCoordinator {
    public interface Backend {
        LocalPlanMachine.Observation observe() throws Exception;
        EffectResult apply(LocalPlanMachine.Request request) throws Exception;
    }

    public static final class EffectResult {
        public final boolean applied;
        public final String preDispatchStateId;
        public EffectResult(boolean applied, String preDispatchStateId) {
            this.applied = applied;
            this.preDispatchStateId = preDispatchStateId == null ? "" : preDispatchStateId;
        }
    }

    private final LocalPlanMachine machine;
    private final LocalPlanJournal journal;
    private final Backend backend;
    private boolean stopped;
    private String stopReason = "";
    private int recordedStep;

    public LocalPlanCoordinator(LocalSkillPlan plan, String taskId,
                                Backend backend, LocalPlanJournal journal) {
        if (backend == null || journal == null) throw new IllegalArgumentException("Missing runtime");
        machine = new LocalPlanMachine(plan, taskId);
        if (!journal.start(taskId)) throw new IllegalStateException("TASK_ID_OR_STORAGE_REJECTED");
        this.backend = backend;
        this.journal = journal;
    }

    public LocalPlanMachine.State state() { return machine.state(); }
    public String reason() { return stopped ? stopReason : machine.reason(); }
    public LocalSkillPlan.StepKind currentStepKind() { return machine.currentStepKind(); }
    public boolean stopped() { return stopped; }

    public void tick(long nowMs) {
        if (stopped) return;
        LocalPlanMachine.Observation observed;
        try {
            observed = backend.observe();
        } catch (Exception e) {
            stopUncertain("OBSERVATION_FAILED");
            return;
        }
        LocalPlanMachine.Event event = machine.advance(observed, nowMs);
        if (event.kind == LocalPlanMachine.EventKind.DISPATCH) {
            LocalPlanMachine.Request request = event.request;
            if (!journal.beforeEffect(request.requestId, event.stepIndex)) {
                stopUncertain("DURABLE_PREPARE_FAILED");
                return;
            }
            EffectResult result;
            try {
                result = backend.apply(request);
                if (result == null) result = new EffectResult(false, "");
            } catch (Exception e) {
                result = new EffectResult(false, "");
            }
            if (!journal.afterEffect(request.requestId, event.stepIndex, result.applied)) {
                stopUncertain("DURABLE_EFFECT_RESULT_FAILED");
                return;
            }
            boolean accepted = machine.acknowledge(request.requestId,
                    result.preDispatchStateId, result.applied, nowMs);
            if (!accepted) {
                finishIfTerminal();
                if (!stopped) stopUncertain("ACK_REJECTED_AFTER_EFFECT");
                return;
            }
        }
        if (machine.stepIndex() != recordedStep) {
            if (!journal.advanced(machine.stepIndex())) {
                stopUncertain("DURABLE_STEP_FAILED");
                return;
            }
            recordedStep = machine.stepIndex();
        }
        finishIfTerminal();
    }

    public boolean cancel() {
        if (stopped || !machine.cancel()) return false;
        stopped = true;
        stopReason = "CANCELLED_NO_UNDO";
        journal.terminal(machine.state(), stopReason, machine.stepIndex());
        return true;
    }

    private void finishIfTerminal() {
        LocalPlanMachine.State s = machine.state();
        if (s == LocalPlanMachine.State.SUCCEEDED || s == LocalPlanMachine.State.FAILED
                || s == LocalPlanMachine.State.CANCELLED) {
            stopped = true;
            stopReason = machine.reason();
            journal.terminal(s, stopReason, machine.stepIndex());
        }
    }

    private void stopUncertain(String reason) {
        machine.cancel();
        stopped = true;
        stopReason = reason;
        // Preserve ambiguous PREPARED/APPLIED outcomes; they must never replay.
        // If no effect was prepared, a durable cancellation is safe.
        if ("RUNNING".equals(journal.status())) {
            journal.terminal(LocalPlanMachine.State.CANCELLED, reason, machine.stepIndex());
        }
    }
}
