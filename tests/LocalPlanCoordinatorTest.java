package io.projectir.mobile;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** End-to-end simulated coordinator + durable ledger, no Android device effects. */
public final class LocalPlanCoordinatorTest {
    private static int checks;
    private static void ok(boolean predicate, String message) {
        if (!predicate) throw new AssertionError("#" + checks + " " + message);
        checks++;
    }

    private static final class MemoryStore implements LocalPlanJournal.Storage {
        final Map<String, String> entries = new HashMap<>();
        boolean failNext;
        @Override public String get(String key, String fallback) {
            return entries.getOrDefault(key, fallback);
        }
        @Override public boolean commit(Map<String, String> values) {
            if (failNext) { failNext = false; return false; }
            entries.putAll(values);
            return true;
        }
    }

    private static LocalPlanMachine.Observation settings() {
        String p = "com.android.settings";
        return new LocalPlanMachine.Observation("settings-state-001", p,
                Arrays.asList("Settings", "Connections", "Connected devices"),
                Arrays.asList(
                        new LocalSemanticSelector.Node(1, p, "Settings", true, false),
                        new LocalSemanticSelector.Node(2, p, "Connections", true, true),
                        new LocalSemanticSelector.Node(3, p, "Connected devices", true, true)),
                false);
    }

    private static LocalPlanMachine.Observation destination() {
        return new LocalPlanMachine.Observation("settings-state-002", "com.android.settings",
                Arrays.asList("Connections", "Wi-Fi", "Bluetooth"),
                Collections.emptyList(), false);
    }

    private static final class FakeBackend implements LocalPlanCoordinator.Backend {
        int phase, launches, clicks;
        boolean changeStateOnApply;
        @Override public LocalPlanMachine.Observation observe() {
            if (phase == 0) return null;
            return phase == 1 ? settings() : destination();
        }
        @Override public LocalPlanCoordinator.EffectResult apply(LocalPlanMachine.Request request) {
            if (request.kind == LocalSkillPlan.StepKind.LAUNCH) {
                launches++;
                phase = 1;
                return new LocalPlanCoordinator.EffectResult(true, "");
            }
            if (request.kind == LocalSkillPlan.StepKind.CLICK_UNIQUE_TEXT) {
                if (changeStateOnApply) {
                    phase = 2;
                    return new LocalPlanCoordinator.EffectResult(false, "settings-state-002");
                }
                ok(phase == 1 && "settings-state-001".equals(request.expectedStateId),
                        "fresh source state required");
                ok(request.selectedNodeId == 2, "exact node required");
                clicks++;
                phase = 2;
                return new LocalPlanCoordinator.EffectResult(true, "settings-state-001");
            }
            return new LocalPlanCoordinator.EffectResult(false, "");
        }
    }

    private static LocalSkillPlan plan() {
        return LocalSkillRegistry.canonicalById("android.settings.connections");
    }

    private static void success(int index) {
        MemoryStore storage = new MemoryStore();
        LocalPlanJournal journal = new LocalPlanJournal(storage);
        FakeBackend backend = new FakeBackend();
        String id = "plan_sim_" + index;
        LocalPlanCoordinator execution = new LocalPlanCoordinator(plan(), id, backend, journal);
        for (int j = 0; j < 30 && !execution.stopped(); j++) {
            execution.tick(j * 150L);
        }
        ok(execution.stopped(), "completed");
        ok(execution.state() == LocalPlanMachine.State.SUCCEEDED, "success");
        ok(backend.launches == 1 && backend.clicks == 1, "exactly once effects");
        ok("SUCCEEDED".equals(journal.status()), "durable success recorded");
        ok(journal.recover(), "terminal remains terminal");
        ok("SUCCEEDED".equals(journal.status()), "no recovered replay");
        boolean duplicate = false;
        try { new LocalPlanCoordinator(plan(), id, backend, journal); }
        catch (IllegalStateException expected) { duplicate = true; }
        ok(duplicate, "duplicate task claim rejected");
    }

    private static void failures() {
        MemoryStore store = new MemoryStore();
        LocalPlanJournal journal = new LocalPlanJournal(store);
        FakeBackend backend = new FakeBackend();
        LocalPlanCoordinator execution = new LocalPlanCoordinator(
                plan(), "ledger_crash_001", backend, journal);
        execution.tick(0);
        ok(backend.launches == 1 && backend.clicks == 0, "only launch happened");
        LocalPlanJournal afterRestart = new LocalPlanJournal(store);
        ok(afterRestart.recover(), "recover");
        ok("INTERRUPTED_UNKNOWN".equals(afterRestart.status()), "interrupted not resumed");
        ok(!afterRestart.start("ledger_crash_001"), "same task cannot replay");

        MemoryStore preparedStore = new MemoryStore();
        LocalPlanJournal prepared = new LocalPlanJournal(preparedStore);
        ok(prepared.start("before_effect_001"), "claim");
        ok(prepared.beforeEffect("before_effect_001_1", 0), "write ahead");
        ok(new LocalPlanJournal(preparedStore).recover(), "recovery of prepared");
        ok("INTERRUPTED_UNKNOWN".equals(prepared.status()), "prepared interrupted");

        MemoryStore changingStore = new MemoryStore();
        FakeBackend changing = new FakeBackend();
        changing.changeStateOnApply = true;
        LocalPlanCoordinator stale = new LocalPlanCoordinator(
                plan(), "stale_ui_001", changing, new LocalPlanJournal(changingStore));
        for (int j = 0; j < 20 && !stale.stopped(); j++) stale.tick(j * 150L);
        ok(stale.state() == LocalPlanMachine.State.FAILED, "stale stopped");
        ok(changing.clicks == 0, "no stale click");

        MemoryStore failStore = new MemoryStore();
        FakeBackend never = new FakeBackend();
        LocalPlanCoordinator fail = new LocalPlanCoordinator(
                plan(), "disk_error_001", never, new LocalPlanJournal(failStore));
        failStore.failNext = true;
        fail.tick(0);
        ok(fail.stopped(), "write-ahead failure halts");
        ok(never.launches == 0, "no effect when durable prepare failed");

        MemoryStore cancelledStore = new MemoryStore();
        FakeBackend cancelledBackend = new FakeBackend();
        LocalPlanCoordinator cancelled = new LocalPlanCoordinator(
                plan(), "cancelled_001", cancelledBackend, new LocalPlanJournal(cancelledStore));
        ok(cancelled.cancel(), "cancel worked");
        cancelled.tick(1);
        ok(cancelledBackend.launches == 0, "cancel prevents launch");
        ok("CANCELLED".equals(new LocalPlanJournal(cancelledStore).status()),
                "cancel recorded");
    }

    public static void main(String[] args) {
        for (int i = 0; i < 1000; i++) success(i);
        failures();
        System.out.println("COORDINATOR_SIMULATED_PLANS=1000");
        System.out.println("COORDINATOR_ASSERTIONS=" + checks);
        System.out.println("COORDINATOR_SIDE_EFFECTS_ON_DEVICE=0");
    }
}
