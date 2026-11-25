package io.projectir.mobile;

public final class ReconnectPolicyTest {
    private static int count = 0;
    private static void verify(boolean expected, String context) {
        if (!expected) throw new AssertionError(context);
        count++;
    }

    public static void main(String[] ignored) {
        ReconnectPolicy policy = new ReconnectPolicy();
        int[] network = {2, 4, 8, 16, 30, 30};
        for (int expected : network) verify(policy.nextDelaySeconds(0) == expected, "network " + expected);
        int[] auth = {60, 120, 240, 480, 960, 1800, 1800};
        for (int expected : auth) verify(policy.nextDelaySeconds(401) == expected, "auth " + expected);
        verify(policy.nextDelaySeconds(503) == 30, "server errors use the network delay");
        policy.connected();
        verify(policy.nextDelaySeconds(0) == 2, "network reset");
        verify(policy.nextDelaySeconds(403) == 60, "auth reset");
        System.out.println("RECONNECT_POLICY_PASS=" + count);
    }
}
