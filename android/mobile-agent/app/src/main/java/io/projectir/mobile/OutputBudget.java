package io.projectir.mobile;

import java.nio.charset.StandardCharsets;

/**
 * Keeps a shell result below the 1 MB Binder buffer, which every concurrent
 * call in the process shares. Sizes are counted as they are sent: UTF-8 bytes
 * after JSON escaping.
 */
final class OutputBudget {
    /** Two concurrent jobs (HubClient's pool) stay below the shared buffer. */
    static final int MAX_JSON_BYTES = 400_000;

    final String stdout;
    final String stderr;
    final boolean stdoutCut;
    final boolean stderrCut;

    private OutputBudget(String stdout, String stderr, boolean stdoutCut, boolean stderrCut) {
        this.stdout = stdout;
        this.stderr = stderr;
        this.stdoutCut = stdoutCut;
        this.stderrCut = stderrCut;
    }

    /** stderr may use a quarter, or more when stdout leaves room; stdout gets the rest. */
    static OutputBudget fit(String stdout, String stderr, int budget) {
        long outCost = jsonBytes(stdout);
        long errCost = jsonBytes(stderr);
        if (outCost + errCost <= budget) return new OutputBudget(stdout, stderr, false, false);
        int errLimit = (int) Math.max(budget / 4L, budget - outCost);
        String err = prefix(stderr, errLimit);
        String out = prefix(stdout, (int) (budget - jsonBytes(err)));
        return new OutputBudget(out, err,
                out.length() < stdout.length(), err.length() < stderr.length());
    }

    static long jsonBytes(String value) {
        long total = 0;
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            total += cost(cp);
            i += Character.charCount(cp);
        }
        return total;
    }

    /** Longest whole-code-point prefix that fits. */
    static String prefix(String value, int budget) {
        long total = 0;
        int i = 0;
        while (i < value.length()) {
            int cp = value.codePointAt(i);
            total += cost(cp);
            if (total > budget) break;
            i += Character.charCount(cp);
        }
        return value.substring(0, i);
    }

    /** Decode captured bytes, dropping a character cut in half by the capture limit. */
    static String decode(byte[] bytes, boolean truncated) {
        int end = bytes.length;
        if (truncated) {
            int lead = end - 1;
            while (lead >= 0 && end - lead < 4 && (bytes[lead] & 0xC0) == 0x80) lead--;
            if (lead >= 0) {
                int b = bytes[lead] & 0xFF;
                int need = b >= 0xF0 ? 4 : b >= 0xE0 ? 3 : b >= 0xC0 ? 2 : 1;
                if (need > end - lead) end = lead;
            }
        }
        return new String(bytes, 0, end, StandardCharsets.UTF_8);
    }

    private static int cost(int cp) {
        if (cp == '"' || cp == '\\' || cp == '/') return 2;
        if (cp == '\n' || cp == '\r' || cp == '\t' || cp == '\b' || cp == '\f') return 2;
        if (cp < 0x20) return 6;
        if (cp < 0x80) return 1;
        if (cp < 0x800) return 2;
        return cp < 0x10000 ? 3 : 4;
    }
}
