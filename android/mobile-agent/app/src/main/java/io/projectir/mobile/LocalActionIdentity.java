package io.projectir.mobile;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure functions for a bounded on-device idempotency journal.
 * Identifiers carry no command text or personal data.
 */
public final class LocalActionIdentity {
    public static final int HISTORY_LIMIT = 64;

    private LocalActionIdentity() {}

    public static boolean valid(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{8,64}");
    }

    public static boolean seen(String journal, String id) {
        if (!valid(id) || journal == null) return false;
        for (String value : journal.split("\\n")) {
            if (id.equals(value)) return true;
        }
        return false;
    }

    public static String append(String journal, String id) {
        if (!valid(id)) throw new IllegalArgumentException("Invalid action ID");
        List<String> values = new ArrayList<>();
        if (journal != null) {
            for (String value : journal.split("\\n")) {
                if (valid(value) && !value.equals(id)) values.add(value);
            }
        }
        values.add(id);
        int start = Math.max(0, values.size() - HISTORY_LIMIT);
        return String.join("\n", values.subList(start, values.size()));
    }
}
