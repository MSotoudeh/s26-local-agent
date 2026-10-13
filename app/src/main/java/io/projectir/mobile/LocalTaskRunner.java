package io.projectir.mobile;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Small on-device task state machine. No Hub, Shizuku, shell, model or network
 * calls are made on this path. Every allowlisted action has a postcondition.
 */
public final class LocalTaskRunner {
    public interface Listener { void onLocalTaskChanged(String status); }

    private static final long TIMEOUT_MS = 6000L;
    private static final long POLL_MS = 150L;
    private static LocalTaskRunner instance;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences preferences;
    private final AtomicInteger generation = new AtomicInteger(0);
    private Listener listener;
    private boolean busy;
    private long startedAt;
    private LocalCommandRouter.Kind active;
    private String phase;
    private String targetPackage;

    private LocalTaskRunner(Context appContext) {
        context = appContext;
        preferences = appContext.getSharedPreferences("local_text_agent", Context.MODE_PRIVATE);
    }

    public static synchronized LocalTaskRunner get(Context context) {
        if (instance == null) instance = new LocalTaskRunner(context.getApplicationContext());
        return instance;
    }

    public void setListener(Listener value) {
        listener = value;
        if (value != null) value.onLocalTaskChanged(currentStatus());
    }

    public String currentStatus() {
        return preferences.getString("status", "IDLE") + " | "
                + preferences.getString("command", "none") + " | "
                + preferences.getString("detail", "No local command yet") + " | "
                + preferences.getLong("elapsed_ms", 0L) + "ms";
    }

    /** Call from the Activity UI only, with a user-entered allowlisted command. */
    public boolean execute(String input) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("LocalTaskRunner must run on the UI loop");
        }
        if (busy) return false;
        LocalCommandRouter.Kind kind = LocalCommandRouter.parse(input);
        if (kind == LocalCommandRouter.Kind.UNSUPPORTED) {
            persist("REJECTED", "UNSUPPORTED", "Command is not in the offline allowlist", 0);
            return false;
        }
        if (kind != LocalCommandRouter.Kind.DEVICE_STATUS
                && UiAccessibilityService.instance() == null) {
            persist("FAIL_CAPABILITY", kind.name(), "Enable Project IR Accessibility first", 0);
            return false;
        }
        active = kind;
        startedAt = SystemClock.elapsedRealtime();
        busy = true;
        int token = generation.incrementAndGet();
        persist("RUNNING", kind.name(), "Local execution started", 0);
        if (kind == LocalCommandRouter.Kind.DEVICE_STATUS) {
            finish(true, "Android API " + Build.VERSION.SDK_INT + " / " + Build.MODEL);
            return true;
        }
        targetPackage = packageFor(kind);
        try {
            Intent launch = new Intent(Intent.ACTION_MAIN);
            launch.addCategory(Intent.CATEGORY_LAUNCHER);
            launch.setClassName(targetPackage, activityFor(kind));
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(launch);
            phase = "WAIT_APP";
            handler.postDelayed(() -> poll(token), POLL_MS);
        } catch (Exception e) {
            finish(false, "App launch failed: " + e.getClass().getSimpleName());
        }
        return true;
    }

    private static String packageFor(LocalCommandRouter.Kind kind) {
        switch (kind) {
            case OPEN_SETTINGS:
            case OPEN_CONNECTIONS: return "com.android.settings";
            case OPEN_CALCULATOR: return "com.sec.android.app.popupcalculator";
            case OPEN_CLOCK: return "com.sec.android.app.clockpackage";
            case OPEN_MAPS: return "com.google.android.apps.maps";
            default: throw new IllegalArgumentException("No app for command");
        }
    }

    private static String activityFor(LocalCommandRouter.Kind kind) {
        switch (kind) {
            case OPEN_SETTINGS:
            case OPEN_CONNECTIONS: return "com.android.settings.Settings";
            case OPEN_CALCULATOR: return "com.sec.android.app.popupcalculator.Calculator";
            case OPEN_CLOCK: return "com.sec.android.app.clockpackage.ClockPackage";
            case OPEN_MAPS: return "com.google.android.maps.MapsActivity";
            default: throw new IllegalArgumentException("No activity for command");
        }
    }

    private void poll(int token) {
        if (!busy || generation.get() != token) return;
        if (SystemClock.elapsedRealtime() - startedAt >= TIMEOUT_MS) {
            finish(false, "Postcondition timeout (" + phase + ")");
            return;
        }
        UiAccessibilityService service = UiAccessibilityService.instance();
        if (service == null) {
            finish(false, "Accessibility disconnected");
            return;
        }
        try {
            if (active != LocalCommandRouter.Kind.OPEN_CONNECTIONS) {
                if (targetPackage.equals(service.activePackageName())) {
                    finish(true, "Foreground package verified");
                    return;
                }
            } else {
                if ("WAIT_APP".equals(phase)) {
                    if (service.hasExactTexts(targetPackage, "Connections", "Wi-Fi", "Bluetooth")) {
                        finish(true, "Connections page verified");
                        return;
                    }
                    if (service.hasExactTexts(targetPackage, "Settings", "Connections", "Connected devices")) {
                        // Unique target and package guard. Never click on ambiguous screens.
                        if (!service.clickUniqueTextInPackage(targetPackage, "Connections")) {
                            finish(false, "Ambiguous/missing Connections action");
                            return;
                        }
                        phase = "VERIFY_CONNECTIONS";
                        persist("RUNNING", active.name(), "Semantic click dispatched", elapsed());
                    }
                } else if ("VERIFY_CONNECTIONS".equals(phase)
                        && service.hasExactTexts(targetPackage, "Connections", "Wi-Fi", "Bluetooth")) {
                    finish(true, "Connections page verified after click");
                    return;
                }
            }
        } catch (Exception e) {
            finish(false, "Verification failed: " + e.getClass().getSimpleName());
            return;
        }
        handler.postDelayed(() -> poll(token), POLL_MS);
    }

    private long elapsed() { return Math.max(0L, SystemClock.elapsedRealtime() - startedAt); }

    private void finish(boolean success, String detail) {
        persist(success ? "PASS_VERIFIED" : "FAIL_VERIFICATION", active.name(), detail, elapsed());
        busy = false;
    }

    private void persist(String status, String command, String detail, long elapsedMs) {
        // No microphone, input text, Accessibility tree or personal content is stored.
        preferences.edit().putString("status", status)
                .putString("command", command)
                .putString("detail", detail)
                .putLong("elapsed_ms", elapsedMs)
                .putLong("updated_at", System.currentTimeMillis())
                .apply();
        if (listener != null) listener.onLocalTaskChanged(currentStatus());
    }
}
