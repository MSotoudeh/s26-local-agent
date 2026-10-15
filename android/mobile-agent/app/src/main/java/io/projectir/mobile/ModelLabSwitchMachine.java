package io.projectir.mobile;

import java.util.HashMap;
import java.util.Map;

/**
 * M1 pure Java, side-effect-free orchestration core for model runtime adapters.
 * Only a verified, registered and locally available runtime can activate.
 * A successful activation is committed after load AND health probe.
 * No shell, file download, network access, raw Android actions or model execution
 * is implemented here. Runtime-specific adapters are added in subsequent stages.
 */
public final class ModelLabSwitchMachine {
    public interface Adapter {
        boolean load() throws Exception;
        boolean health() throws Exception;
        void unload() throws Exception;
    }
    public interface Registry {
        boolean verifiedAndAllowed(String task, String modelId);
        Adapter create(String task, String modelId) throws Exception;
    }
    public interface Journal {
        String activeId(String task);
        boolean commit(String task, String modelId);
    }
    public enum Outcome {
        ACTIVATED, ALREADY_ACTIVE, NOT_VERIFIED, INVALID,
        CANDIDATE_LOAD_FAILED, CANDIDATE_HEALTH_FAILED,
        JOURNAL_COMMIT_FAILED, ROLLED_BACK, ROLLBACK_FAILED, BUSY
    }
    public static final class Receipt {
        public final Outcome outcome;
        public final String task;
        public final String requestedId;
        public final String activeId;
        public final boolean rollbackHealthy;
        Receipt(Outcome outcome, String task, String requestedId,
                String activeId, boolean rollbackHealthy) {
            this.outcome = outcome;
            this.task = task;
            this.requestedId = requestedId;
            this.activeId = activeId;
            this.rollbackHealthy = rollbackHealthy;
        }
    }
    private final Registry registry;
    private final Journal journal;
    private final Map<String, Adapter> loaded = new HashMap<>();
    private boolean busy;

    public ModelLabSwitchMachine(Registry registry, Journal journal) {
        if (registry == null || journal == null) throw new IllegalArgumentException("null adapter");
        this.registry = registry;
        this.journal = journal;
    }

    public synchronized boolean busy() { return busy; }

    public synchronized Receipt activate(String task, String modelId) {
        if (busy) return receipt(Outcome.BUSY, task, modelId, false);
        if (!valid(task) || !valid(modelId)) {
            return receipt(Outcome.INVALID, task, modelId, false);
        }
        if (!registry.verifiedAndAllowed(task, modelId)) {
            return receipt(Outcome.NOT_VERIFIED, task, modelId, false);
        }
        if (modelId.equals(journal.activeId(task)) && loaded.containsKey(task)) {
            return receipt(Outcome.ALREADY_ACTIVE, task, modelId, true);
        }
        busy = true;
        try {
            final String previousId = journal.activeId(task);
            final Adapter old = loaded.remove(task);
            if (old != null) {
                try { old.unload(); } catch (Exception e) {
                    // Do not let a leaked heavyweight model overlap a new one.
                    loaded.put(task, old);
                    return receipt(Outcome.ROLLBACK_FAILED, task, modelId, false);
                }
            }
            Adapter candidate = null;
            boolean loadedCandidate = false;
            boolean healthy = false;
            try {
                candidate = registry.create(task, modelId);
                if (candidate != null) {
                    loadedCandidate = candidate.load();
                    if (loadedCandidate) healthy = candidate.health();
                }
            } catch (Exception | LinkageError ex) {
                // Fail closed; attempt to recover the prior model.
            }
            if (!loadedCandidate || !healthy) {
                safeUnload(candidate);
                boolean restored = restore(task, previousId, old);
                return receipt(restored ? Outcome.ROLLED_BACK : Outcome.ROLLBACK_FAILED,
                        task, modelId, restored);
            }
            boolean persisted = false;
            try { persisted = journal.commit(task, modelId); }
            catch (RuntimeException e) { /* fail-closed journal */ }
            if (!persisted) {
                safeUnload(candidate);
                boolean restored = restore(task, previousId, old);
                return receipt(restored ? Outcome.ROLLED_BACK : Outcome.ROLLBACK_FAILED,
                        task, modelId, restored);
            }
            loaded.put(task, candidate);
            return receipt(Outcome.ACTIVATED, task, modelId, true);
        } finally {
            busy = false;
        }
    }

    public synchronized void unload(String task) {
        if (busy) return;
        Adapter active = loaded.remove(task);
        safeUnload(active);
    }

    private boolean restore(String task, String oldId, Adapter old) {
        if (oldId == null || oldId.isEmpty()) return true;
        Adapter restored = old;
        try {
            if (restored == null) restored = registry.create(task, oldId);
            if (restored != null && restored.load() && restored.health()) {
                loaded.put(task, restored);
                return true;
            }
        } catch (Exception | LinkageError ignored) { }
        safeUnload(restored);
        return false;
    }

    private Receipt receipt(Outcome outcome, String task, String id, boolean healthy) {
        return new Receipt(outcome, task, id,
                task == null ? "" : journal.activeId(task), healthy);
    }

    private static boolean valid(String s) {
        return s != null && !s.isEmpty() && s.length() <= 160
                && s.matches("[a-zA-Z0-9_.:-]+") && !s.contains("..");
    }

    private static void safeUnload(Adapter adapter) {
        if (adapter == null) return;
        try { adapter.unload(); } catch (Exception ignored) { }
    }
}
