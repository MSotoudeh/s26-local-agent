package io.projectir.mobile;

import java.util.List;

public final class ResultOutboxTest {
    private static int count = 0;
    private static void verify(boolean expected, String context) {
        if (!expected) throw new AssertionError(context);
        count++;
    }

    public static void main(String[] ignored) {
        ResultOutbox box = new ResultOutbox();
        verify(box.pending(0).isEmpty(), "starts empty");
        box.add("a", "ra", 1_000);
        box.add("b", "rb", 2_000);
        verify(box.pending(3_000).equals(List.of("ra", "rb")), "oldest first");
        verify(box.pending(3_000).size() == 2, "reading does not remove: delivery is not acknowledged");
        box.add("a", "ra2", 4_000);
        verify(box.pending(4_000).equals(List.of("rb", "ra2")), "one entry per job");
        verify(box.pending(2_000 + ResultOutbox.MAX_AGE_MS).equals(List.of("rb", "ra2")), "age limit is inclusive");
        verify(box.pending(2_001 + ResultOutbox.MAX_AGE_MS).equals(List.of("ra2")), "old results expire");
        verify(box.pending(4_001 + ResultOutbox.MAX_AGE_MS).isEmpty(), "all expire");

        ResultOutbox full = new ResultOutbox();
        for (int i = 0; i < ResultOutbox.MAX_RESULTS + 3; i++) full.add("j" + i, "r" + i, i);
        List<String> kept = full.pending(10);
        verify(kept.size() == ResultOutbox.MAX_RESULTS, "bounded");
        verify(kept.get(0).equals("r3"), "oldest dropped first");
        System.out.println("RESULT_OUTBOX_PASS=" + count);
    }
}
