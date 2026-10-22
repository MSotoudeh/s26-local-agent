package io.projectir.mobile;

/**
 * Device status is local-first: Hub transport, Shizuku Binder and the privileged
 * UserService are distinct failure domains. Notification/mDNS state is not a
 * source of truth for execution privileges.
 *
 * Plain Java so the decision can be regression-tested on the host JVM.
 */
final class AgentHealthState {
    private AgentHealthState() {}

    static String shellStatus(boolean binderAlive, boolean permissionGranted,
                              int uid, boolean userServiceReady) {
        if (!binderAlive) return "Shell: unavailable";
        if (uid != 2000) return "Shell: unexpected UID";
        if (!permissionGranted) return "Shell: permission required";
        if (!userServiceReady) return "Shell: reconnecting";
        return "Shell: ready (uid 2000)";
    }

    static String compose(String hubState, boolean binderAlive, boolean permissionGranted,
                          int uid, boolean userServiceReady) {
        String state = hubState == null || hubState.isBlank() ? "unknown" : hubState;
        // Transport is an untrusted diagnostic: avoid multiline UI injection
        // or accidentally exposing a long exception message.
        state = state.replaceAll("[\\r\\n\\t]", " ").trim();
        if (state.length() > 70) state = state.substring(0, 70);
        return "Hub: " + state + " | "
                + shellStatus(binderAlive, permissionGranted, uid, userServiceReady);
    }
}
