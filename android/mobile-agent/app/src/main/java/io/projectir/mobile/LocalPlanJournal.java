package io.projectir.mobile;

import java.util.HashMap;
import java.util.Map;

/**
 * Write-ahead, privacy-preserving ledger for a single local plan.
 * A crash never causes a pending effect to be replayed.
 * No input text, screen contents, selectors or credentials are recorded.
 */
public final class LocalPlanJournal {
    public interface Storage {
        String get(String key, String fallback);
        boolean commit(Map<String, String> updates);
    }

    private static final String PREFIX = "plan.";
    private final Storage store;

    public LocalPlanJournal(Storage store) {
        if (store == null) throw new IllegalArgumentException("Storage required");
        this.store = store;
    }

    public String status() { return store.get(PREFIX + "status", "IDLE"); }
    public boolean seenId(String id) {
        return LocalActionIdentity.seen(store.get(PREFIX + "recent_ids", ""), id);
    }
    public String taskId() { return store.get(PREFIX + "task_id", ""); }
    public String requestId() { return store.get(PREFIX + "request_id", ""); }
    public String reason() { return store.get(PREFIX + "reason", ""); }
    public String step() { return store.get(PREFIX + "step", "0"); }

    /** Called only on new process/runtime creation. Never resume uncertain work. */
    public boolean recover() {
        String s = status();
        if ("RUNNING".equals(s) || "PREPARED".equals(s) || "APPLIED".equals(s)) {
            return update("INTERRUPTED_UNKNOWN", requestId(), step(),
                    "PROCESS_INTERRUPTED_EFFECT_UNKNOWN");
        }
        return true;
    }

    /** Atomically claims a task ID before any effect; rejects duplicate claims. */
    public boolean start(String id) {
        if (!LocalActionIdentity.valid(id)) return false;
        String history = store.get(PREFIX + "recent_ids", "");
        if (LocalActionIdentity.seen(history, id)) return false;
        String s = status();
        if ("RUNNING".equals(s) || "PREPARED".equals(s) || "APPLIED".equals(s)) return false;
        Map<String, String> entries = new HashMap<>();
        entries.put(PREFIX + "recent_ids", LocalActionIdentity.append(history, id));
        entries.put(PREFIX + "task_id", id);
        entries.put(PREFIX + "status", "RUNNING");
        entries.put(PREFIX + "request_id", "");
        entries.put(PREFIX + "step", "0");
        entries.put(PREFIX + "reason", "STARTED");
        return store.commit(entries);
    }

    public boolean beforeEffect(String id, int index) {
        if (!"RUNNING".equals(status()) || id == null || id.isEmpty()
                || !id.startsWith(taskId() + "_")) return false;
        return update("PREPARED", id, String.valueOf(index), "EFFECT_NOT_YET_ACKNOWLEDGED");
    }

    public boolean afterEffect(String id, int index, boolean success) {
        if (!"PREPARED".equals(status()) || !id.equals(requestId())) return false;
        return update(success ? "APPLIED" : "UNCERTAIN", id,
                String.valueOf(index), success ? "EFFECT_RESULT_REQUIRES_VERIFICATION"
                        : "EFFECT_REJECTED_OR_UNKNOWN");
    }

    /** An ACK is not task success; later steps still need observation. */
    public boolean advanced(int index) {
        String s = status();
        if (!"RUNNING".equals(s) && !"APPLIED".equals(s)) return false;
        return update("RUNNING", "", String.valueOf(index), "AWAITING_POSTCONDITION");
    }

    public boolean terminal(LocalPlanMachine.State state, String reason, int index) {
        if (state != LocalPlanMachine.State.SUCCEEDED
                && state != LocalPlanMachine.State.FAILED
                && state != LocalPlanMachine.State.CANCELLED) return false;
        return update(state.name(), "", String.valueOf(index), reason);
    }

    private boolean update(String status, String request, String step, String reason) {
        Map<String, String> changes = new HashMap<>();
        changes.put(PREFIX + "status", status);
        changes.put(PREFIX + "request_id", request);
        changes.put(PREFIX + "step", step);
        changes.put(PREFIX + "reason", reason);
        return store.commit(changes);
    }
}
