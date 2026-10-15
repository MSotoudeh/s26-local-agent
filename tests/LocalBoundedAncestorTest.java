package io.projectir.mobile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Models the actual 2026-10-15 Samsung S26 Settings Connections hierarchy. */
public final class LocalBoundedAncestorTest {
    private static int checks;
    private static final String PKG = "com.android.settings";
    private static final String STATE = "live-settings-state-001";

    private static void ok(boolean condition, String message) {
        if (!condition) throw new AssertionError(message + " check=" + checks);
        checks++;
    }

    private static List<LocalSemanticSelector.Node> hierarchy() {
        return Arrays.asList(
                new LocalSemanticSelector.Node(1, -1, PKG, "Settings", true, false),
                new LocalSemanticSelector.Node(38, 1, PKG, "", true, false),
                new LocalSemanticSelector.Node(43, 38, PKG, "", true, true),
                new LocalSemanticSelector.Node(63, 43, PKG, "", true, false),
                new LocalSemanticSelector.Node(93, 63, PKG, "Connections", true, false)
        );
    }

    private static LocalSemanticSelector.Decision decide(
            List<LocalSemanticSelector.Node> nodes, String expected, String actual,
            String requested) {
        return LocalSemanticSelector.decide(expected, actual,
                PKG, PKG, requested, nodes, false);
    }

    private static void cases() {
        LocalSemanticSelector.Decision accepted =
                decide(hierarchy(), STATE, STATE, "Connections");
        ok(accepted.accepted, "bounded two-parent selector accepts S26 hierarchy");
        ok(accepted.nodeId == 43, "select exact actionable ancestor ID");
        ok("UNIQUE_BOUNDED_PARENT".equals(accepted.reason), "classified parent action");

        List<LocalSemanticSelector.Node> duplicated = new ArrayList<>(hierarchy());
        duplicated.add(new LocalSemanticSelector.Node(94, 43, PKG,
                "Connections", true, true));
        ok("AMBIGUOUS_TARGET".equals(
                decide(duplicated, STATE, STATE, "Connections").reason),
                "reject duplicate exact leaf");

        List<LocalSemanticSelector.Node> missing = Arrays.asList(
                new LocalSemanticSelector.Node(1, -1, PKG, "Connections", true, false));
        ok("TARGET_NOT_ACTIONABLE".equals(
                decide(missing, STATE, STATE, "Connections").reason),
                "no invented ancestor");

        List<LocalSemanticSelector.Node> outside = Arrays.asList(
                new LocalSemanticSelector.Node(12, -1, "com.unknown", "", true, true),
                new LocalSemanticSelector.Node(13, 12, PKG, "Connections", true, false));
        ok("TARGET_NOT_ACTIONABLE".equals(
                decide(outside, STATE, STATE, "Connections").reason),
                "reject cross-package parent");

        List<LocalSemanticSelector.Node> disabled = Arrays.asList(
                new LocalSemanticSelector.Node(12, -1, PKG, "", false, true),
                new LocalSemanticSelector.Node(13, 12, PKG, "Connections", true, false));
        ok("TARGET_NOT_ACTIONABLE".equals(
                decide(disabled, STATE, STATE, "Connections").reason),
                "disabled ancestor rejected");

        List<LocalSemanticSelector.Node> wrongText = Arrays.asList(
                new LocalSemanticSelector.Node(12, -1, PKG, "Delete all", true, true),
                new LocalSemanticSelector.Node(13, 12, PKG, "Connections", true, false));
        ok("TARGET_NOT_ACTIONABLE".equals(
                decide(wrongText, STATE, STATE, "Connections").reason),
                "conflicting ancestor rejected");

        List<LocalSemanticSelector.Node> tooFar = Arrays.asList(
                new LocalSemanticSelector.Node(10, -1, PKG, "", true, true),
                new LocalSemanticSelector.Node(11, 10, PKG, "", true, false),
                new LocalSemanticSelector.Node(12, 11, PKG, "", true, false),
                new LocalSemanticSelector.Node(13, 12, PKG, "", true, false),
                new LocalSemanticSelector.Node(14, 13, PKG, "Connections", true, false));
        ok("TARGET_NOT_ACTIONABLE".equals(
                decide(tooFar, STATE, STATE, "Connections").reason),
                "more than three parent edges rejected");

        List<LocalSemanticSelector.Node> cycle = Arrays.asList(
                new LocalSemanticSelector.Node(11, 12, PKG, "", true, false),
                new LocalSemanticSelector.Node(12, 11, PKG, "", true, false),
                new LocalSemanticSelector.Node(13, 11, PKG, "Connections", true, false));
        ok("TARGET_NOT_ACTIONABLE".equals(
                decide(cycle, STATE, STATE, "Connections").reason),
                "parent cycle cannot yield action");

        ok("STALE_STATE".equals(
                decide(hierarchy(), STATE, "different-state-001", "Connections").reason),
                "UI state change fails closed");
        ok(!LocalSemanticSelector.decide(STATE, STATE, PKG, "com.other",
                "Connections", hierarchy(), false).accepted, "package mismatch");
        ok(!LocalSemanticSelector.decide(STATE, STATE, PKG, PKG,
                "Connections", hierarchy(), true).accepted, "truncated tree");

        List<LocalSemanticSelector.Node> textAction = Arrays.asList(
                new LocalSemanticSelector.Node(15, -1, PKG, "Connections", true, true));
        ok(decide(textAction, STATE, STATE, "Connections").nodeId == 15,
                "directly clickable target works");

        LocalPlanMachine.Observation observation = new LocalPlanMachine.Observation(
                STATE, PKG,
                Arrays.asList("Settings", "Connections", "Connected devices"),
                hierarchy(), false);
        LocalPlanMachine machine = new LocalPlanMachine(
                LocalSkillRegistry.canonicalById("android.settings.connections"),
                "real_ancestor_fixture_01");
        LocalPlanMachine.Event event = machine.advance(null, 0L);
        ok(event.request != null, "launch event");
        ok(machine.acknowledge(event.request.requestId, "", true, 20L), "launch ack");
        machine.advance(observation, 80L);
        LocalPlanMachine.Event click = machine.advance(observation, 90L);
        ok(click.request != null && click.request.selectedNodeId == 43,
                "machine dispatches bounded actionable ancestor");
    }

    public static void main(String[] args) {
        for (int i = 0; i < 1000; i++) cases();
        System.out.println("BOUNDED_PARENT_FIXTURES=1000");
        System.out.println("BOUNDED_PARENT_ASSERTIONS=" + checks);
        System.out.println("ON_DEVICE_UI_EFFECTS=0");
    }
}
