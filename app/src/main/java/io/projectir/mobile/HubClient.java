package io.projectir.mobile;

import android.content.Context;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public final class HubClient {
    private static final String TAG = "ProjectIRHubClient";

    public interface StateListener {
        void onState(String state);
    }

    private final ConfigStore.Config config;
    private final StateListener stateListener;
    private final ShellBridge shell;
    private final OkHttpClient http;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService jobs = Executors.newFixedThreadPool(2);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    private final AtomicInteger reconnectSeconds = new AtomicInteger(2);
    private final AtomicLong completed = new AtomicLong(0);
    private final AtomicLong failed = new AtomicLong(0);

    private volatile WebSocket socket;

    public HubClient(Context context, ConfigStore.Config config, StateListener stateListener) {
        this.config = config;
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
        state("connecting");
        try {
            String hub = config.hubUrl;
            String wsBase = hub.startsWith("https://")
                    ? "wss://" + hub.substring("https://".length())
                    : "ws://" + hub.substring("http://".length());
            String url = wsBase + "/agent/connect?device_id="
                    + URLEncoder.encode(config.deviceId, StandardCharsets.UTF_8.name());
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + config.token)
                    .build();
            socket = http.newWebSocket(request, new Listener());
        } catch (Throwable t) {
            state("connect-error:" + t.getClass().getSimpleName());
            scheduleReconnect();
        }
    }

    private void send(JSONObject payload) {
        WebSocket local = socket;
        if (local != null) local.send(payload.toString());
    }

    private void sendHello() {
        try {
            JSONObject hello = new JSONObject()
                    .put("type", "hello")
                    .put("device_id", config.deviceId)
                    .put("agent_version", "android-0.1.0")
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
                send(new JSONObject()
                        .put("type", "result")
                        .put("job_id", jobId)
                        .put("ok", true)
                        .put("result", result));
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

    private void scheduleReconnect() {
        if (stopped.get() || !reconnectScheduled.compareAndSet(false, true)) return;
        int seconds = reconnectSeconds.get();
        scheduler.schedule(() -> {
            reconnectScheduled.set(false);
            if (!stopped.get()) connect();
        }, seconds, TimeUnit.SECONDS);
        reconnectSeconds.set(Math.min(30, Math.max(2, seconds * 2)));
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
            socket = webSocket;
            reconnectSeconds.set(2);
            reconnectScheduled.set(false);
            state("connected");
            sendHello();
            shell.ensureBound();
            scheduler.execute(HubClient.this::heartbeat);
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
            if (socket == webSocket) socket = null;
            if (!stopped.get()) {
                state("disconnected");
                scheduleReconnect();
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            if (socket == webSocket) socket = null;
            if (!stopped.get()) {
                state("network-error:" + t.getClass().getSimpleName());
                scheduleReconnect();
            }
        }
    }
}
