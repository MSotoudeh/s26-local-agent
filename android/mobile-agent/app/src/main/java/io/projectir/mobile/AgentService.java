package io.projectir.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public final class AgentService extends Service {
    private static final String CHANNEL_ID = "project_ir_agent";
    private static final int NOTIFICATION_ID = 4110;

    public static volatile String runtimeState = "stopped";
    private HubClient client;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, notification("starting"));
        startClient();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (client == null) startClient();
        return START_STICKY;
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
        client = new HubClient(this, config, state -> {
            runtimeState = state;
            updateNotification(state);
        });
        client.start();
    }

    @Override
    public void onDestroy() {
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
        getSystemService(NotificationManager.class)
                .notify(NOTIFICATION_ID, notification(state));
    }
}
