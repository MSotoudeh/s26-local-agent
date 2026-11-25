package io.projectir.mobile;

/**
 * Reconnect delays for the outbound Hub connection.
 *
 * Network failures are retried quickly. A refused credential cannot fix itself,
 * so it is retried slowly: before this, a 401 was retried every 30 seconds
 * forever, which only cost battery.
 */
final class ReconnectPolicy {
    static final int NETWORK_MIN_SECONDS = 2;
    static final int NETWORK_MAX_SECONDS = 30;
    static final int AUTH_MIN_SECONDS = 60;
    static final int AUTH_MAX_SECONDS = 30 * 60;

    private int network = NETWORK_MIN_SECONDS;
    private int auth = AUTH_MIN_SECONDS;

    static boolean isAuthFailure(int httpStatus) {
        return httpStatus == 401 || httpStatus == 403;
    }

    enum NetworkAction { NONE, CONNECT, REPLACE }

    /**
     * What to do when a default network becomes available.
     * A live socket on another network is replaced: it would otherwise stay
     * "connected" until a ping fails. A refused credential keeps its slow retry.
     */
    static NetworkAction onNetwork(boolean connected, boolean sameNetwork, boolean waitingForAuth) {
        if (connected) return sameNetwork ? NetworkAction.NONE : NetworkAction.REPLACE;
        return waitingForAuth ? NetworkAction.NONE : NetworkAction.CONNECT;
    }

    /** The old delay was about the old network; a refused credential stays refused. */
    synchronized void networkChanged() {
        network = NETWORK_MIN_SECONDS;
    }

    /** Delay before the next attempt. httpStatus is 0 when there was no response. */
    synchronized int nextDelaySeconds(int httpStatus) {
        int delay;
        if (isAuthFailure(httpStatus)) {
            delay = auth;
            auth = Math.min(AUTH_MAX_SECONDS, auth * 2);
        } else {
            delay = network;
            network = Math.min(NETWORK_MAX_SECONDS, network * 2);
        }
        return delay;
    }

    synchronized void connected() {
        network = NETWORK_MIN_SECONDS;
        auth = AUTH_MIN_SECONDS;
    }
}
