package io.projectir.mobile;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class UiAccessibilityService extends AccessibilityService {
    private static final int DEFAULT_MAX_NODES = 500;
    private static volatile UiAccessibilityService instance;

    private final AtomicLong eventSequence = new AtomicLong(0L);
    private volatile long lastEventElapsedMs = 0L;
    private volatile int lastEventType = 0;
    private volatile String lastEventPackage = "";

    public static UiAccessibilityService instance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        lastEventType = event.getEventType();
        CharSequence pkg = event.getPackageName();
        lastEventPackage = pkg == null ? "" : pkg.toString();
        lastEventElapsedMs = SystemClock.elapsedRealtime();
        eventSequence.incrementAndGet();
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public JSONObject statusJson() {
        JSONObject out = new JSONObject();
        put(out, "connected", true);
        put(out, "eventSequence", eventSequence.get());
        put(out, "lastEventElapsedMs", lastEventElapsedMs);
        put(out, "lastEventType", lastEventType);
        put(out, "lastEventPackage", lastEventPackage);
        return out;
    }

    public JSONObject snapshotJson() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        JSONObject out = statusJson();
        JSONArray nodes = new JSONArray();
        put(out, "nodes", nodes);
        if (root == null) {
            put(out, "rootAvailable", false);
            return out;
        }

        put(out, "rootAvailable", true);
        CharSequence rootPkg = root.getPackageName();
        put(out, "packageName", rootPkg == null ? JSONObject.NULL : rootPkg.toString());

        ArrayDeque<NodeRecord> queue = new ArrayDeque<>();
        queue.add(new NodeRecord(root, -1));
        int nextId = 0;
        while (!queue.isEmpty() && nextId < DEFAULT_MAX_NODES) {
            NodeRecord record = queue.removeFirst();
            AccessibilityNodeInfo node = record.node;
            if (node == null) continue;

            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);

            int id = nextId++;
            JSONObject item = new JSONObject();
            put(item, "id", id);
            put(item, "parent", record.parentId);
            put(item, "text", nullable(node.getText()));
            put(item, "contentDescription", nullable(node.getContentDescription()));
            put(item, "resourceId", nullable(node.getViewIdResourceName()));
            put(item, "className", nullable(node.getClassName()));
            put(item, "packageName", nullable(node.getPackageName()));
            put(item, "bounds", bounds.flattenToString());
            put(item, "clickable", node.isClickable());
            put(item, "editable", node.isEditable());
            put(item, "focused", node.isFocused());
            put(item, "enabled", node.isEnabled());
            put(item, "scrollable", node.isScrollable());
            put(item, "actions", node.getActions());
            nodes.put(item);

            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.addLast(new NodeRecord(child, id));
            }
        }

        put(out, "nodeCount", nodes.length());
        put(out, "truncated", !queue.isEmpty());
        return out;
    }

    /** Current package, obtained locally without the Hub or Shizuku. */
    public String activePackageName() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) return "";
        return root.getPackageName().toString();
    }

    /** Bounded, read-only postcondition check; never stores screen content. */
    public boolean hasExactTexts(String expectedPackage, String... labels) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null
                || !expectedPackage.contentEquals(root.getPackageName())) return false;
        boolean[] found = new boolean[labels.length];
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int checked = 0;
        while (!queue.isEmpty() && checked++ < DEFAULT_MAX_NODES) {
            AccessibilityNodeInfo node = queue.removeFirst();
            CharSequence content = node.getText();
            for (int i = 0; i < labels.length; i++) {
                if (content != null && labels[i].contentEquals(content)) found[i] = true;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.addLast(child);
            }
        }
        for (boolean hit : found) if (!hit) return false;
        return true;
    }

    /** Click only one exact semantic match in the expected foreground package. */
    public boolean clickUniqueTextInPackage(String expectedPackage, String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null
                || !expectedPackage.contentEquals(root.getPackageName())) return false;
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        AccessibilityNodeInfo candidate = null;
        int hits = 0;
        int checked = 0;
        while (!queue.isEmpty() && checked++ < DEFAULT_MAX_NODES) {
            AccessibilityNodeInfo node = queue.removeFirst();
            CharSequence value = node.getText();
            if (value != null && text.contentEquals(value)) {
                candidate = node;
                hits++;
                if (hits > 1) return false;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.addLast(child);
            }
        }
        return hits == 1 && queue.isEmpty() && clickNodeOrAncestor(candidate);
    }

    /**
     * Fail-closed canonical-plan click. A uniquely labelled leaf may delegate
     * ACTION_CLICK to a bounded same-package ancestor. The exact actionable
     * BFS ID must still equal the engine's pre-dispatch selection.
     *
     * The tree is freshly traversed and selector policy re-run immediately
     * before the effect. No coordinate click, guessed parent, or long ascent.
     */
    public boolean clickVerifiedNodeInPackage(String expectedPackage, String label,
                                              int expectedNodeId) {
        if (expectedPackage == null || label == null || expectedNodeId < 0) return false;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null
                || !expectedPackage.contentEquals(root.getPackageName())) return false;
        ArrayDeque<NodeRecord> queue = new ArrayDeque<>();
        queue.add(new NodeRecord(root, -1));
        ArrayList<AccessibilityNodeInfo> actual = new ArrayList<>();
        ArrayList<LocalSemanticSelector.Node> logical = new ArrayList<>();

        while (!queue.isEmpty() && actual.size() < DEFAULT_MAX_NODES) {
            NodeRecord entry = queue.removeFirst();
            AccessibilityNodeInfo node = entry.node;
            int id = actual.size();
            actual.add(node);
            CharSequence pkg = node.getPackageName();
            CharSequence text = node.getText();
            logical.add(new LocalSemanticSelector.Node(id, entry.parentId,
                    pkg == null ? "" : pkg.toString(),
                    text == null ? "" : text.toString(),
                    node.isEnabled(), node.isClickable()));
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.addLast(new NodeRecord(child, id));
            }
        }
        if (!queue.isEmpty()) return false; // fail closed on truncated tree
        LocalSemanticSelector.Decision decision = LocalSemanticSelector.decide(
                "live-state", "live-state", expectedPackage, expectedPackage,
                label, logical, false);
        if (!decision.accepted || decision.nodeId != expectedNodeId
                || expectedNodeId >= actual.size()) return false;
        AccessibilityNodeInfo target = actual.get(expectedNodeId);
        if (!expectedPackage.equals(activePackageName())
                || !target.isEnabled() || !target.isClickable()) return false;
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    public boolean clickExactText(String text) {
        if (text == null || text.isEmpty()) return false;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.removeFirst();
            CharSequence current = node.getText();
            if (current != null && text.contentEquals(current)) {
                if (clickNodeOrAncestor(node)) return true;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.addLast(child);
            }
        }
        return false;
    }

    public boolean clickResource(String resourceId) {
        if (resourceId == null || resourceId.isEmpty()) return false;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByViewId(resourceId)) {
            if (clickNodeOrAncestor(node)) return true;
        }
        return false;
    }

    public boolean inputFocused(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        AccessibilityNodeInfo focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focus == null || !focus.isEditable()) return false;

        Bundle args = new Bundle();
        args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text == null ? "" : text
        );
        return focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    private boolean clickNodeOrAncestor(AccessibilityNodeInfo start) {
        AccessibilityNodeInfo node = start;
        for (int depth = 0; node != null && depth < 8; depth++) {
            if (node.isEnabled()
                    && node.getActionList().contains(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            node = node.getParent();
        }
        return false;
    }

    private static void put(JSONObject object, String key, Object value) {
        try {
            object.put(key, value);
        } catch (JSONException e) {
            throw new IllegalStateException("JSON encode failed for " + key, e);
        }
    }

    private static Object nullable(CharSequence value) {
        return value == null ? JSONObject.NULL : value.toString();
    }

    private static Object nullable(String value) {
        return value == null ? JSONObject.NULL : value;
    }

    private static final class NodeRecord {
        final AccessibilityNodeInfo node;
        final int parentId;

        NodeRecord(AccessibilityNodeInfo node, int parentId) {
            this.node = node;
            this.parentId = parentId;
        }
    }
}
