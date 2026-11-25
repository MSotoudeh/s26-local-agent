package io.projectir.mobile;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

public final class AgentService extends Service {
    private static final String CHANNEL_ID = "project_ir_agent";
    private static final int NOTIFICATION_ID = 4110;

    /** Reconnect with the saved configuration even if a client is already running. */
    static final String ACTION_RELOAD = "io.projectir.mobile.action.RELOAD";

    /** Wake-up alarm: timers and pings do not run while the CPU sleeps. */
    private static final String ACTION_KEEP_ALIVE = "io.projectir.mobile.action.KEEP_ALIVE";
    private static final long KEEP_ALIVE_MS = 9 * 60 * 1000L;

    public static volatile String runtimeState = "stopped";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable localHealthPoll = new Runnable() {
        @Override public void run() {
            // Binder recovery is local. Do not wait for a Hub connection or
            // infer health from ShizukuPlus's mDNS foreground notification.
            if (shell != null && shell.hasPermission() && !shell.isShellServiceReady()) {
                shell.ensureBound();
            }
            updateNotification(hubState);
            mainHandler.postDelayed(this, 10_000L);
        }
    };
    private volatile String hubState = "starting";
    private String lastNotificationState;
    private HubClient client;
    private ShellBridge shell;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        shell = ShellBridge.get(this);
        startForeground(NOTIFICATION_ID, notification("Hub: starting | Shell: checking"));
        startClient();
        mainHandler.postDelayed(localHealthPoll, 1_000L);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean reload = intent != null && ACTION_RELOAD.equals(intent.getAction());
        if (client == null || reload) startClient();
        if (client != null) {
            if (intent != null && ACTION_KEEP_ALIVE.equals(intent.getAction())) client.keepAlive();
            scheduleKeepAlive();
        }
        return START_STICKY;
    }

    private void scheduleKeepAlive() {
        // Inexact and allowed while idle: no exact-alarm permission needed.
        getSystemService(AlarmManager.class).setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + KEEP_ALIVE_MS, keepAliveIntent());
    }

    private PendingIntent keepAliveIntent() {
        return PendingIntent.getForegroundService(this, 1,
                new Intent(this, AgentService.class).setAction(ACTION_KEEP_ALIVE),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void startClient() {
        ConfigStore.Config config = new ConfigStore(this).load();
        if (!config.ready()) {
            runtimeState = "not-configured";
            updateNotification(runtimeState);
            stopSelf();
            return;
        }
        if (client != null) client.stop();
        client = new HubClient(this, config, state ->
                mainHandler.post(() -> updateNotification(state)));
        client.start();
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacks(localHealthPoll);
        getSystemService(AlarmManager.class).cancel(keepAliveIntent());
        if (client != null) {
            client.stop();
            client = null;
        }
        runtimeState = "stopped";
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Project IR Mobile Agent",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Persistent outbound Project IR connection");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification notification(String state) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(
                this,
                0,
                open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Project IR Mobile Agent")
                .setContentText("State: " + state)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .setContentIntent(pending)
                .build();
    }

    private void updateNotification(String state) {
        hubState = state;
        // Binder ping and UserService readiness are checked independently of
        // Hub connectivity, notifications and mDNS discovery. Fail closed.
        boolean alive = shell != null && shell.isShizukuRunning();
        int uid = alive ? shell.shizukuUid() : -1;
        boolean permitted = alive && shell.hasPermission();
        boolean ready = permitted && shell.isShellServiceReady();
        String combined = AgentHealthState.compose(state, alive, permitted, uid, ready);
        runtimeState = combined;
        if (combined.equals(lastNotificationState)) return;
        lastNotificationState = combined;
        getSystemService(NotificationManager.class)
                .notify(NOTIFICATION_ID, notification(combined));
    }
}
