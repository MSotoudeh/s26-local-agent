package io.projectir.mobile;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recent job results, sent again after a reconnect.
 *
 * The Hub does not acknowledge results, and a socket that has silently lost its
 * network still accepts writes, so a result sent just before a connection broke
 * may be gone. The Hub stores results by job id, so sending one twice is safe.
 */
final class ResultOutbox {
    static final int MAX_RESULTS = 8;
    static final long MAX_AGE_MS = 120_000L;

    private static final class Entry {
        final String payload;
        final long at;

        Entry(String payload, long at) {
            this.payload = payload;
            this.at = at;
        }
    }

    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

    synchronized void add(String jobId, String payload, long nowMs) {
        entries.remove(jobId);
        entries.put(jobId, new Entry(payload, nowMs));
        while (entries.size() > MAX_RESULTS) {
            Iterator<String> oldest = entries.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /** Results that may not have arrived, oldest first. */
    synchronized List<String> pending(long nowMs) {
        entries.values().removeIf(entry -> nowMs - entry.at > MAX_AGE_MS);
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Entry> entry : entries.entrySet()) out.add(entry.getValue().payload);
        return out;
    }
}
