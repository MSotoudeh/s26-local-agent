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

        // A new network must not wait out an old delay, but must not hammer a refused credential either.
        ReconnectPolicy moved = new ReconnectPolicy();
        for (int i = 0; i < 5; i++) moved.nextDelaySeconds(0);
        moved.nextDelaySeconds(401);
        moved.networkChanged();
        verify(moved.nextDelaySeconds(0) == 2, "network change resets the network delay");
        verify(moved.nextDelaySeconds(401) == 120, "network change keeps the auth delay");

        verify(ReconnectPolicy.onNetwork(true, true, false) == ReconnectPolicy.NetworkAction.NONE, "same network");
        verify(ReconnectPolicy.onNetwork(true, false, false) == ReconnectPolicy.NetworkAction.REPLACE, "moved");
        verify(ReconnectPolicy.onNetwork(false, true, false) == ReconnectPolicy.NetworkAction.CONNECT, "back");
        verify(ReconnectPolicy.onNetwork(false, false, false) == ReconnectPolicy.NetworkAction.CONNECT, "new");
        verify(ReconnectPolicy.onNetwork(false, false, true) == ReconnectPolicy.NetworkAction.NONE, "auth wait");
        System.out.println("RECONNECT_POLICY_PASS=" + count);
    }
}
