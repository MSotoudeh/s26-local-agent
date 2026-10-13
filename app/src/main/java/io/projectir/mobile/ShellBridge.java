package io.projectir.mobile;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import rikka.shizuku.Shizuku;

public final class ShellBridge {
    public static final int PERMISSION_REQUEST_CODE = 41001;

    private static volatile ShellBridge instance;

    private final Object lock = new Object();
    private final AtomicBoolean binding = new AtomicBoolean(false);
    private final Shizuku.UserServiceArgs serviceArgs;
    private volatile IShellService service;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = IShellService.Stub.asInterface(binder);
            binding.set(false);
            synchronized (lock) {
                lock.notifyAll();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            binding.set(false);
        }
    };

    private final Shizuku.OnBinderReceivedListener binderReceived = this::ensureBound;
    private final Shizuku.OnBinderDeadListener binderDead = () -> {
        service = null;
        binding.set(false);
    };

    private ShellBridge(Context context) {
        Context app = context.getApplicationContext();
        this.serviceArgs = new Shizuku.UserServiceArgs(
                new ComponentName(app, ShellUserService.class)
        ).processNameSuffix("project_ir_shell")
                .daemon(true)
                .version(1);
        Shizuku.addBinderReceivedListenerSticky(binderReceived);
        Shizuku.addBinderDeadListener(binderDead);
        ensureBound();
    }

    public static void initialize(Context context) {
        get(context);
    }

    public static ShellBridge get(Context context) {
        ShellBridge local = instance;
        if (local == null) {
            synchronized (ShellBridge.class) {
                local = instance;
                if (local == null) {
                    local = new ShellBridge(context);
                    instance = local;
                }
            }
        }
        return local;
    }

    public boolean isShizukuRunning() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public int shizukuUid() {
        try {
            return isShizukuRunning() ? Shizuku.getUid() : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    public boolean hasPermission() {
        try {
            return isShizukuRunning()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public boolean isShellServiceReady() {
        IShellService local = service;
        return local != null && local.asBinder().isBinderAlive();
    }

    public void requestPermission() {
        if (!isShizukuRunning()) {
            throw new IllegalStateException("Shizuku is not running");
        }
        if (!hasPermission()) {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE);
        }
    }

    public void ensureBound() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new android.os.Handler(Looper.getMainLooper()).post(this::ensureBound);
            return;
        }
        if (isShellServiceReady() || !hasPermission()) return;
        if (!binding.compareAndSet(false, true)) return;
        try {
            Shizuku.bindUserService(serviceArgs, connection);
        } catch (Throwable t) {
            binding.set(false);
        }
    }

    public JSONObject exec(String command, String cwd, Map<String, ?> env, int timeoutSeconds) throws Exception {
        ensureBound();
        long deadline = System.currentTimeMillis() + 6000;
        synchronized (lock) {
            while (!isShellServiceReady() && System.currentTimeMillis() < deadline) {
                lock.wait(Math.max(1, deadline - System.currentTimeMillis()));
            }
        }
        IShellService local = service;
        if (local == null || !local.asBinder().isBinderAlive()) {
            if (!isShizukuRunning()) throw new IllegalStateException("Shizuku is not running");
            if (!hasPermission()) throw new SecurityException("Shizuku permission is not granted");
            throw new IllegalStateException("Shizuku UserService is not ready");
        }

        JSONObject envJson = new JSONObject();
        if (env != null) {
            for (Map.Entry<String, ?> entry : env.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    envJson.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
        }
        String raw = local.exec(
                command,
                cwd == null ? "" : cwd,
                envJson.toString(),
                timeoutSeconds
        );
        JSONObject result = new JSONObject(raw);
        if (result.has("error_type")) {
            throw new RuntimeException(
                    result.optString("error_type") + ": " + result.optString("error_message")
            );
        }
        return result;
    }
}
