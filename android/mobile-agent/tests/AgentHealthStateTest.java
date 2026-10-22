package io.projectir.mobile;

/** Host-side fail-closed status regression without Android dependencies. */
public final class AgentHealthStateTest {
    private static int count = 0;
    private static void verify(boolean expected, String context) {
        if (!expected) throw new AssertionError(context);
        count++;
    }

    public static void main(String[] ignored) {
        verify(AgentHealthState.compose("network-error:ProtocolException", true, true, 2000, true)
                .equals("Hub: network-error:ProtocolException | Shell: ready (uid 2000)"),
                "Hub failure must not overwrite healthy shell");
        verify(AgentHealthState.compose("connected", false, true, 2000, true)
                .equals("Hub: connected | Shell: unavailable"),
                "Transport connected is not binder health");
        verify(AgentHealthState.compose("connected", true, false, 2000, true)
                .endsWith("Shell: permission required"), "permission");
        verify(AgentHealthState.compose("connected", true, true, 2000, false)
                .endsWith("Shell: reconnecting"), "user service");
        verify(AgentHealthState.compose("connected", true, true, 0, true)
                .endsWith("Shell: unexpected UID"), "root is not accepted as shell UID");
        verify(AgentHealthState.compose("connected", true, true, -1, true)
                .endsWith("Shell: unexpected UID"), "unknown UID");
        verify(AgentHealthState.compose("HTTP 401\\nsecret", true, true, 2000, true)
                .equals("Hub: HTTP 401\\nsecret | Shell: ready (uid 2000)"),
                "literal slash-n should be retained (not newline)");
        verify(!AgentHealthState.compose("untrusted\nnotification", true, true, 2000, true)
                .contains("\n"), "multiline sanitized");
        verify(AgentHealthState.compose(null, false, false, -1, false)
                .startsWith("Hub: unknown"), "null hub state");
        verify(AgentHealthState.compose("x".repeat(300), false, false, -1, false)
                .length() < 110, "bounded notification size");
        System.out.println("AGENT_HEALTH_STATE_PASS=" + count);
    }
}
