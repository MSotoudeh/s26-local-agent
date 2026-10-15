package io.projectir.mobile;

/** Standalone host-JVM regression suite; no Android runtime required. */
public final class LocalActionIdentityTest {
    private static int checks = 0;
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Check #" + checks + " failed");
        checks++;
    }
    public static void main(String[] args) {
        check(!LocalActionIdentity.valid(null));
        check(!LocalActionIdentity.valid(""));
        check(!LocalActionIdentity.valid("short"));
        check(!LocalActionIdentity.valid("bad id 123"));
        check(!LocalActionIdentity.valid("../bad-id"));
        check(LocalActionIdentity.valid("run_00000001"));
        check(!LocalActionIdentity.seen("", "run_00000001"));
        String journal = LocalActionIdentity.append("", "run_00000001");
        check(LocalActionIdentity.seen(journal, "run_00000001"));
        journal = LocalActionIdentity.append(journal, "run_00000002");
        check(LocalActionIdentity.seen(journal, "run_00000001"));
        check(LocalActionIdentity.seen(journal, "run_00000002"));
        journal = LocalActionIdentity.append(journal, "run_00000001");
        check(journal.indexOf("run_00000001") == journal.lastIndexOf("run_00000001"));
        for (int i = 0; i < 1000; i++) {
            journal = LocalActionIdentity.append(journal, String.format("run_%08d", i));
        }
        check(journal.split("\\n").length == LocalActionIdentity.HISTORY_LIMIT);
        check(!LocalActionIdentity.seen(journal, "run_00000000"));
        check(LocalActionIdentity.seen(journal, "run_00000999"));
        check(LocalActionIdentity.seen(journal, "run_00000936"));
        check(!LocalActionIdentity.seen(journal, "run_00000935"));
        try {
            LocalActionIdentity.append(journal, "wrong");
            throw new AssertionError("Expected invalid action ID rejection");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        System.out.println("LOCAL_ACTION_IDENTITY_PASS=" + checks);
    }
}
