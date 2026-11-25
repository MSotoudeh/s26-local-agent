package io.projectir.mobile;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public final class HubClient {
    private static final String TAG = "ProjectIRHubClient";

    public interface StateListener {
        void onState(String state);
    }

    private final ConfigStore store;
    private final ConfigStore.Config config;
    private final String agentVersion;
    private final StateListener stateListener;
    private final ShellBridge shell;
    private final OkHttpClient http;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService jobs = Executors.newFixedThreadPool(2);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    private final ReconnectPolicy reconnect = new ReconnectPolicy();
    private final AtomicLong completed = new AtomicLong(0);
    private final AtomicLong failed = new AtomicLong(0);

    /** Connection that owns the current state; older sockets' callbacks are ignored. */
    private volatile Listener current;
    private volatile WebSocket socket;
    // Both are used on the scheduler thread only.
    private String token;
    private boolean enrollmentPending;

    public HubClient(Context context, ConfigStore.Config config, StateListener stateListener) {
        this.store = new ConfigStore(context);
        this.config = config;
        this.token = config.token;
        this.enrollmentPending = config.hasPendingEnrollment();
        this.agentVersion = agentVersion(context);
        this.stateListener = stateListener;
        this.shell = ShellBridge.get(context.getApplicationContext());
        this.http = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
    }

    public void start() {
        scheduler.execute(this::connect);
        // A lost UserService binder must recover without reopening the UI.
        // Keep this check independent of WebSocket connectivity.
        scheduler.scheduleWithFixedDelay(() -> {
            if (!stopped.get() && shell.hasPermission() && !shell.isShellServiceReady()) {
                shell.ensureBound();
            }
        }, 5, 10, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::heartbeat, 20, 20, TimeUnit.SECONDS);
    }

    public void stop() {
        if (!stopped.compareAndSet(false, true)) return;
        state("stopped");
        current = null;
        WebSocket local = socket;
        socket = null;
        if (local != null) local.close(1000, "agent stopped");
        scheduler.shutdownNow();
        jobs.shutdownNow();
        http.dispatcher().executorService().shutdown();
        http.connectionPool().evictAll();
    }

    private void connect() {
        if (stopped.get()) return;
        try {
            if (enrollmentPending && !enroll()) return;
            if (token.isBlank()) {
                // Nothing to authenticate with, and only the owner can fix it.
                state("not-enrolled");
                return;
            }
            state("connecting");
            String url = "wss://" + config.hubUrl.substring("https://".length())
                    + "/agent/connect" + deviceQuery();
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + token)
                    .build();
            // Publish the owner before the socket exists: a callback may run
            // before newWebSocket() returns, and must not be taken for stale.
            Listener listener = new Listener();
            current = listener;
            socket = http.newWebSocket(request, listener);
        } catch (Throwable t) {
            state("connect-error:" + t.getClass().getSimpleName());
            scheduleReconnect(0);
        }
    }

    /**
     * Register the token generated on this phone with the one-time code.
     * Returns true when connecting may continue. Codes and tokens are never logged.
     */
    private boolean enroll() {
        state("enrolling");
        try {
            int status = post("/agent/enroll", null,
                    HubEnrollment.requestBody(config.pendingCode, config.pendingToken));
            HubEnrollment.Outcome outcome = HubEnrollment.classify(status);
            if (outcome == HubEnrollment.Outcome.VERIFY) {
                // The code works once. If an earlier reply was lost, the Hub
                // already knows this token; check before calling it refused.
                int verified = post("/agent/credential/verify" + deviceQuery(),
                        config.pendingToken, "");
                if (verified == 200) outcome = HubEnrollment.Outcome.ENROLLED;
                else if (!ReconnectPolicy.isAuthFailure(verified)) outcome = HubEnrollment.Outcome.RETRY;
            }
            switch (outcome) {
                case ENROLLED:
                    if (!store.promotePending(config.pendingToken)) {
                        throw new IllegalStateException("credential not saved");
                    }
                    token = config.pendingToken;
                    enrollmentPending = false;
                    return true;
                case RETRY:
                    state("enroll-retry:HTTP" + status);
                    // Slow on purpose: the Hub blocks a device after repeated attempts.
                    scheduleReconnect(401);
                    return false;
                default:
                    store.discardPending();
                    enrollmentPending = false;
                    state("enroll-refused:HTTP" + status);
                    Log.w(TAG, "enrollment refused: HTTP" + status);
                    // An older credential may still be valid.
                    return !token.isBlank();
            }
        } catch (Exception e) {
            state("enroll-error:" + e.getClass().getSimpleName());
            scheduleReconnect(0);
            return false;
        }
    }

    private int post(String path, String bearer, String json) throws Exception {
        Request.Builder request = new Request.Builder()
                .url(config.hubUrl + path)
                .post(RequestBody.create(json, MediaType.get("application/json; charset=utf-8")));
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        // The shared client has no read timeout (WebSocket); this call needs one.
        OkHttpClient bounded = http.newBuilder().callTimeout(20, TimeUnit.SECONDS)
                .followRedirects(false).build();
        try (Response response = bounded.newCall(request.build()).execute()) {
            return response.code();
        }
    }

    private String deviceQuery() throws Exception {
        return "?device_id=" + URLEncoder.encode(config.deviceId, StandardCharsets.UTF_8.name());
    }

    private static String agentVersion(Context context) {
        try {
            return "android-" + context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "android-unknown";
        }
    }

    private boolean send(JSONObject payload) {
        WebSocket local = socket;
        return local != null && local.send(payload.toString());
    }

    private void sendHello() {
        try {
            JSONObject hello = new JSONObject()
                    .put("type", "hello")
                    .put("device_id", config.deviceId)
                    .put("agent_version", agentVersion)
                    .put("transport", "websocket")
                    .put("concurrency", 2)
                    .put("at", System.currentTimeMillis() / 1000.0)
                    .put("platform", "android")
                    .put("android_sdk", Build.VERSION.SDK_INT)
                    .put("model", Build.MODEL)
                    .put("capabilities", new JSONArray()
                            .put("exec")
                            .put("android")
                            .put("shizuku-user-service"));
            send(hello);
        } catch (Exception e) {
            Log.e(TAG, "hello failed", e);
        }
    }

    private void heartbeat() {
        if (stopped.get() || socket == null) return;
        try {
            send(new JSONObject()
                    .put("type", "heartbeat")
                    .put("device_id", config.deviceId)
                    .put("jobs_completed", completed.get())
                    .put("jobs_failed", failed.get())
                    .put("shizuku_running", shell.isShizukuRunning())
                    .put("shizuku_uid", shell.shizukuUid())
                    .put("shizuku_permission", shell.hasPermission())
                    .put("shizuku_user_service_ready", shell.isShellServiceReady())
                    .put("at", System.currentTimeMillis() / 1000.0));
        } catch (Exception e) {
            Log.e(TAG, "heartbeat failed", e);
        }
    }

    private void handleJob(JSONObject job) {
        final String jobId = job.optString("job_id", "");
        final String tool = job.optString("tool", "");
        final JSONObject args = job.optJSONObject("args") == null
                ? new JSONObject()
                : job.optJSONObject("args");
        if (jobId.isBlank()) return;

        try {
            send(new JSONObject()
                    .put("type", "started")
                    .put("job_id", jobId)
                    .put("at", System.currentTimeMillis() / 1000.0));
        } catch (Exception ignored) {
        }

        jobs.execute(() -> {
            try {
                if (!"local_exec".equals(tool)) {
                    throw new IllegalArgumentException("unsupported Android agent tool: " + tool);
                }
                JSONObject result = executeLocalExec(args);
                boolean delivered = send(new JSONObject()
                        .put("type", "result")
                        .put("job_id", jobId)
                        .put("ok", true)
                        .put("result", result));
                if (!delivered) Log.w(TAG, "result not delivered: connection lost");
                completed.incrementAndGet();
            } catch (Throwable t) {
                failed.incrementAndGet();
                try {
                    send(new JSONObject()
                            .put("type", "result")
                            .put("job_id", jobId)
                            .put("ok", false)
                            .put("error", new JSONObject()
                                    .put("type", t.getClass().getSimpleName())
                                    .put("message", safeMessage(t))));
                } catch (Exception ignored) {
                }
            }
        });
    }

    private JSONObject executeLocalExec(JSONObject args) throws Exception {
        String command = args.optString("command", "");
        String cwd = args.isNull("cwd") ? "" : args.optString("cwd", "");
        int timeout = Math.max(1, Math.min(args.optInt("timeout_seconds", 60), 300));
        Map<String, Object> env = new LinkedHashMap<>();
        JSONObject envJson = args.optJSONObject("env");
        if (envJson != null) {
            Iterator<String> keys = envJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = envJson.opt(key);
                if (value != null && value != JSONObject.NULL) env.put(key, value);
            }
        }
        return shell.exec(command, cwd, Collections.unmodifiableMap(env), timeout);
    }

    private void scheduleReconnect(int httpStatus) {
        if (stopped.get() || !reconnectScheduled.compareAndSet(false, true)) return;
        try {
            scheduler.schedule(() -> {
                reconnectScheduled.set(false);
                if (!stopped.get()) connect();
            }, reconnect.nextDelaySeconds(httpStatus), TimeUnit.SECONDS);
        } catch (java.util.concurrent.RejectedExecutionException stopping) {
            reconnectScheduled.set(false);
        }
    }

    private void state(String value) {
        try {
            stateListener.onState(value);
        } catch (Throwable ignored) {
        }
    }

    private static String safeMessage(Throwable t) {
        String message = t.getMessage();
        return message == null || message.isBlank() ? t.toString() : message;
    }

    private final class Listener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            if (current != this) {
                webSocket.close(1000, "superseded");
                return;
            }
            socket = webSocket;
            reconnect.connected();
            state("connected");
            sendHello();
            shell.ensureBound();
            if (!stopped.get()) scheduler.execute(HubClient.this::heartbeat);
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                JSONObject payload = new JSONObject(text);
                if ("job".equals(payload.optString("type"))) {
                    handleJob(payload);
                }
            } catch (Exception e) {
                Log.e(TAG, "invalid hub message", e);
            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            webSocket.close(code, reason);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            if (current != this) return; // Ignore a superseded connection.
            current = null;
            socket = null;
            if (!stopped.get()) {
                state("disconnected");
                scheduleReconnect(0);
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            if (current != this) return; // Stale socket cannot replace current status.
            current = null;
            socket = null;
            if (!stopped.get()) {
                // Report the handshake HTTP status, if present, without ever
                // logging the Authorization header, body, token, or Hub URL.
                String failure = response == null
                        ? t.getClass().getSimpleName()
                        : "HTTP" + response.code() + ":" + t.getClass().getSimpleName();
                Log.w(TAG, "WebSocket connection failure: " + failure);
                state("network-error:" + failure);
                scheduleReconnect(response == null ? 0 : response.code());
            }
        }
    }
}
