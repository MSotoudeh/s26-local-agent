package io.projectir.mobile;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * On-device execution of ONLY locally registered, read-only navigation skills.
 * Uses Android Accessibility; neither Wi-Fi, the Hub nor Shizuku is required.
 * No plan is automatically resumed after app process death or reboot.
 */
public final class LocalAndroidPlanRuntime {
    public interface Listener { void changed(String status); }

    private static final long POLL_MS = 150L;
    private static LocalAndroidPlanRuntime instance;

    private final Context app;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LocalPlanJournal journal;
    private LocalPlanCoordinator coordinator;
    private Listener listener;
    private int generation;

    private LocalAndroidPlanRuntime(Context app) {
        this.app = app.getApplicationContext();
        SharedPreferences prefs = this.app.getSharedPreferences(
                "local_plan_journal", Context.MODE_PRIVATE);
        journal = new LocalPlanJournal(new LocalPlanJournal.Storage() {
            @Override
            public String get(String key, String fallback) {
                return prefs.getString(key, fallback);
            }
            @Override
            public boolean commit(Map<String, String> values) {
                SharedPreferences.Editor editor = prefs.edit();
                for (Map.Entry<String, String> entry : values.entrySet()) {
                    editor.putString(entry.getKey(), entry.getValue());
                }
                return editor.commit();
            }
        });
        if (!journal.recover()) throw new IllegalStateException("JOURNAL_RECOVERY_FAILED");
    }

    public static synchronized LocalAndroidPlanRuntime get(Context context) {
        if (instance == null) instance = new LocalAndroidPlanRuntime(context);
        return instance;
    }

    public boolean busy() { return coordinator != null && !coordinator.stopped(); }

    public boolean seenId(String id) { return journal.seenId(id); }

    public void setListener(Listener value) {
        listener = value;
        notifyStatus();
    }

    public String status() {
        return journal.status() + " | step=" + journal.step() + " | " + journal.reason();
    }

    public boolean start(String spokenOrTypedCommand, String taskId) {
        requireMain();
        if (busy() || UiAccessibilityService.instance() == null
                || LocalTaskRunner.get(app).isBusy()
                || LocalTaskRunner.get(app).hasSeenId(taskId)) return false;
        LocalSkillPlan plan = LocalSkillRegistry.lookup(spokenOrTypedCommand);
        if (!LocalPlanPolicy.mayExecute(plan)) return false;
        try {
            coordinator = new LocalPlanCoordinator(plan, taskId, new AccessibilityBackend(), journal);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return false;
        }
        final int token = ++generation;
        notifyStatus();
        handler.post(() -> poll(token));
        return true;
    }

    public boolean cancel() {
        requireMain();
        if (!busy()) return false;
        ++generation;
        boolean result = coordinator.cancel();
        notifyStatus();
        return result;
    }

    private void poll(int token) {
        if (token != generation || coordinator == null || coordinator.stopped()) return;
        coordinator.tick(SystemClock.elapsedRealtime());
        notifyStatus();
        if (!coordinator.stopped()) {
            handler.postDelayed(() -> poll(token), POLL_MS);
        }
    }

    private void notifyStatus() {
        if (listener != null) listener.changed(status());
    }

    private static void requireMain() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("Local plan must execute on the main looper");
        }
    }

    private static final class Snapshot {
        final LocalPlanMachine.Observation observation;
        final long ageMs;
        Snapshot(LocalPlanMachine.Observation observation, long ageMs) {
            this.observation = observation;
            this.ageMs = ageMs;
        }
    }

    private final class AccessibilityBackend implements LocalPlanCoordinator.Backend {
        private final LocalObservationGate observationGate = new LocalObservationGate();

        @Override
        public LocalPlanMachine.Observation observe() throws Exception {
            Snapshot state = readSnapshot();
            // Selection and package arrival require a quiet, repeated UI tree.
            // Final VERIFY_TEXTS is read-only and may verify the complete
            // destination tree immediately after a real action. Holding it
            // behind a global Accessibility quiet timer caused false timeouts
            // while Samsung rendered the already-correct destination.
            if (coordinator != null
                    && coordinator.currentStepKind() == LocalSkillPlan.StepKind.VERIFY_TEXTS) {
                return state == null ? null : state.observation;
            }
            return observationGate.accept(state == null ? null : state.observation,
                    state == null ? 0L : state.ageMs);
        }

        @Override
        public LocalPlanCoordinator.EffectResult apply(LocalPlanMachine.Request request)
                throws Exception {
            if (request.kind == LocalSkillPlan.StepKind.LAUNCH) {
                String activity = launchableActivity(request.expectedPackage);
                if (activity == null) return new LocalPlanCoordinator.EffectResult(false, "");
                Intent launch = new Intent(Intent.ACTION_MAIN);
                launch.addCategory(Intent.CATEGORY_LAUNCHER);
                launch.setClassName(request.expectedPackage, activity);
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                try {
                    app.startActivity(launch);
                    return new LocalPlanCoordinator.EffectResult(true, "");
                } catch (Exception e) {
                    return new LocalPlanCoordinator.EffectResult(false, "");
                }
            }
            if (request.kind == LocalSkillPlan.StepKind.CLICK_UNIQUE_TEXT) {
                UiAccessibilityService service = UiAccessibilityService.instance();
                Snapshot fresh = readSnapshot();
                if (service == null || fresh == null || fresh.ageMs < 120L) {
                    return new LocalPlanCoordinator.EffectResult(false, "");
                }
                LocalPlanMachine.Observation state = fresh.observation;
                LocalSemanticSelector.Decision decision = LocalSemanticSelector.decide(
                        request.expectedStateId, state.stateId,
                        request.expectedPackage, state.packageName,
                        request.selector, state.nodes, state.truncated);
                if (!decision.accepted || decision.nodeId != request.selectedNodeId) {
                    return new LocalPlanCoordinator.EffectResult(false, state.stateId);
                }
                // The service itself re-reads the current root, verifies the
                // selected BFS ID and unique text, and never clicks an ancestor.
                boolean applied = service.clickVerifiedNodeInPackage(
                        request.expectedPackage, request.selector, request.selectedNodeId);
                return new LocalPlanCoordinator.EffectResult(applied, state.stateId);
            }
            return new LocalPlanCoordinator.EffectResult(false, "");
        }

        private Snapshot readSnapshot() throws Exception {
            UiAccessibilityService service = UiAccessibilityService.instance();
            if (service == null) return null;
            JSONObject json = service.snapshotJson();
            if (!json.optBoolean("rootAvailable", false)) return null;
            String pkg = json.optString("packageName", "");
            if ("null".equals(pkg) || pkg.isEmpty()) return null;
            JSONArray array = json.optJSONArray("nodes");
            if (array == null) return null;
            boolean truncated = json.optBoolean("truncated", true);
            List<String> labels = new ArrayList<>();
            List<LocalSemanticSelector.Node> nodes = new ArrayList<>();
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            add(hash, pkg);
            add(hash, Boolean.toString(truncated));
            for (int i = 0; i < array.length(); i++) {
                JSONObject node = array.getJSONObject(i);
                String nodePkg = value(node, "packageName");
                String nodeText = value(node, "text");
                int id = node.optInt("id", -1);
                boolean clickable = node.optBoolean("clickable", false);
                boolean enabled = node.optBoolean("enabled", false);
                if (!nodeText.isEmpty()) labels.add(nodeText);
                nodes.add(new LocalSemanticSelector.Node(id, node.optInt("parent", -1),
                        nodePkg, nodeText, enabled, clickable));
                // Fixed-order structured digest, independent of JSON map order.
                for (String key : new String[]{"id", "parent", "text",
                        "contentDescription", "resourceId", "className",
                        "packageName", "bounds", "clickable", "enabled",
                        "scrollable", "actions"}) {
                    add(hash, key);
                    add(hash, value(node, key));
                }
            }
            byte[] digest = hash.digest();
            StringBuilder id = new StringBuilder("ui-");
            for (int i = 0; i < 12; i++) {
                id.append(Character.forDigit((digest[i] >> 4) & 15, 16));
                id.append(Character.forDigit(digest[i] & 15, 16));
            }
            long eventMs = json.optLong("lastEventElapsedMs", 0L);
            long ageMs = eventMs > 0L
                    ? Math.max(0L, SystemClock.elapsedRealtime() - eventMs) : 0L;
            return new Snapshot(new LocalPlanMachine.Observation(
                    id.toString(), pkg, labels, nodes, truncated), ageMs);
        }

        private void add(MessageDigest hash, String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            hash.update((byte) (bytes.length >> 8));
            hash.update((byte) bytes.length);
            hash.update(bytes);
        }

        private String value(JSONObject o, String key) {
            Object value = o.opt(key);
            return value == null || value == JSONObject.NULL ? "" : String.valueOf(value);
        }
    }

    private static String launchableActivity(String pkg) {
        switch (pkg) {
            case "com.android.settings": return "com.android.settings.Settings";
            case "com.sec.android.app.popupcalculator":
                return "com.sec.android.app.popupcalculator.Calculator";
            case "com.sec.android.app.clockpackage":
                return "com.sec.android.app.clockpackage.ClockPackage";
            case "com.google.android.apps.maps": return "com.google.android.maps.MapsActivity";
            default: return null;
        }
    }
}
