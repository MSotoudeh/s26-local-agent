package io.projectir.mobile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stateless, fail-closed, exact semantic target selection.
 * The selected text must be unique and its eventual action node must be
 * either directly clickable or the closest enabled, same-package clickable
 * ancestor within at most three parent edges. No unbounded parent climbing.
 * This class never dispatches an Android action.
 */
public final class LocalSemanticSelector {
    private static final int MAX_NODES = 500;
    private static final int MAX_PARENT_EDGES = 3;

    private LocalSemanticSelector() {}

    public static final class Node {
        public final int id;
        public final int parentId;
        public final String packageName;
        public final String text;
        public final boolean enabled;
        public final boolean clickable;

        /** Test/backward-compatible constructor for flat node inventories. */
        public Node(int id, String packageName, String text,
                    boolean enabled, boolean clickable) {
            this(id, -1, packageName, text, enabled, clickable);
        }

        public Node(int id, int parentId, String packageName, String text,
                    boolean enabled, boolean clickable) {
            this.id = id;
            this.parentId = parentId;
            this.packageName = packageName == null ? "" : packageName;
            this.text = text == null ? "" : text;
            this.enabled = enabled;
            this.clickable = clickable;
        }
    }

    public static final class Decision {
        public final boolean accepted;
        /** The ACTUAL clickable node ID; may be a bounded ancestor. */
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

    private static Decision reject(String reason, String stateId) {
        return new Decision(false, -1, reason, stateId);
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
        if (truncated || nodes == null || nodes.size() > MAX_NODES) {
            return reject("INCOMPLETE_TREE", currentState);
        }
        if (requestedLabel == null || requestedLabel.trim().isEmpty()) {
            return reject("INVALID_TARGET", currentState);
        }

        String normalized = LocalSkillRegistry.normalize(requestedLabel);
        Map<Integer, Node> index = new HashMap<>();
        Node matched = null;
        int matches = 0;
        for (Node node : nodes) {
            if (node == null || node.id < 0 || index.putIfAbsent(node.id, node) != null) {
                return reject("INVALID_NODE_TREE", currentState);
            }
            if (!expectedPackage.equals(node.packageName)) continue;
            if (normalized.equals(LocalSkillRegistry.normalize(node.text))) {
                matches++;
                if (matches > 1) return reject("AMBIGUOUS_TARGET", currentState);
                matched = node;
            }
        }
        if (matches == 0) return reject("TARGET_NOT_VISIBLE", currentState);
        if (!matched.enabled) return reject("TARGET_NOT_ACTIONABLE", currentState);
        if (matched.clickable) {
            return new Decision(true, matched.id, "EXACT_UNIQUE", currentState);
        }

        // Only climb the canonical parent chain from one exact text match.
        // Crossing package boundaries, disabled or text-conflicting containers
        // is unsafe. A missing or cyclic parent chain fails closed.
        Node child = matched;
        for (int depth = 1; depth <= MAX_PARENT_EDGES; depth++) {
            Node parent = index.get(child.parentId);
            if (parent == null || parent.id == child.id
                    || !expectedPackage.equals(parent.packageName)
                    || !parent.enabled) {
                return reject("TARGET_NOT_ACTIONABLE", currentState);
            }
            String parentLabel = LocalSkillRegistry.normalize(parent.text);
            if (!parentLabel.isEmpty() && !normalized.equals(parentLabel)) {
                return reject("TARGET_NOT_ACTIONABLE", currentState);
            }
            if (parent.clickable) {
                return new Decision(true, parent.id, "UNIQUE_BOUNDED_PARENT", currentState);
            }
            child = parent;
        }
        return reject("TARGET_NOT_ACTIONABLE", currentState);
    }
}
