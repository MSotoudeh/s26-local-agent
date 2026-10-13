package io.projectir.mobile;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stateless, exact and bounded UI decision. This class NEVER executes a click.
 * A caller must recheck the state identity immediately before using the ID.
 */
public final class LocalSemanticSelector {
    private LocalSemanticSelector() {}

    public static final class Node {
        public final int id;
        public final String packageName;
        public final String text;
        public final boolean enabled;
        public final boolean clickable;

        public Node(int id, String packageName, String text,
                    boolean enabled, boolean clickable) {
            this.id = id;
            this.packageName = packageName;
            this.text = text;
            this.enabled = enabled;
            this.clickable = clickable;
        }
    }

    public static final class Decision {
        public final boolean accepted;
        public final int nodeId;
        public final String reason;
        public final String stateId;
        private Decision(boolean accepted, int nodeId, String reason, String stateId) {
            this.accepted = accepted;
            this.nodeId = nodeId;
            this.reason = reason;
            this.stateId = stateId;
        }
    }

    private static Decision reject(String why, String stateId) {
        return new Decision(false, -1, why, stateId);
    }

    public static Decision decide(String expectedState, String currentState,
                                  String expectedPackage, String actualPackage,
                                  String requestedLabel, List<Node> nodes,
                                  boolean truncated) {
        if (expectedState == null || expectedState.length() < 8
                || !expectedState.equals(currentState)) {
            return reject("STALE_STATE", currentState);
        }
        if (expectedPackage == null || !expectedPackage.equals(actualPackage)) {
            return reject("PACKAGE_MISMATCH", currentState);
        }
        if (truncated || nodes == null || nodes.size() > 500) {
            return reject("INCOMPLETE_TREE", currentState);
        }
        if (requestedLabel == null || requestedLabel.trim().isEmpty()) {
            return reject("INVALID_TARGET", currentState);
        }
        String label = LocalSkillRegistry.normalize(requestedLabel);
        Set<Integer> allIds = new HashSet<>();
        Node selected = null;
        int textMatches = 0;
        for (Node n : nodes) {
            if (n == null || n.id < 0 || !allIds.add(n.id)) {
                return reject("INVALID_NODE_TREE", currentState);
            }
            if (!expectedPackage.equals(n.packageName)) continue;
            if (label.equals(LocalSkillRegistry.normalize(n.text))) {
                textMatches++;
                if (textMatches > 1) return reject("AMBIGUOUS_TARGET", currentState);
                selected = n;
            }
        }
        if (textMatches == 0) return reject("TARGET_NOT_VISIBLE", currentState);
        if (!selected.enabled || !selected.clickable) {
            return reject("TARGET_NOT_ACTIONABLE", currentState);
        }
        return new Decision(true, selected.id, "EXACT_UNIQUE", currentState);
    }
}
