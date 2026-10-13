package io.projectir.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ConfigStore store;
    private ShellBridge shell;
    private EditText hub;
    private EditText device;
    private EditText token;
    private CheckBox autostart;
    private TextView status;
    private EditText localCommand;
    private TextView localStatus;

    private final Shizuku.OnRequestPermissionResultListener permissionListener =
            (requestCode, grantResult) -> {
                if (requestCode == ShellBridge.PERMISSION_REQUEST_CODE) {
                    shell.ensureBound();
                    refreshStatus();
                }
            };

    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            handler.postDelayed(this, 1500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ConfigStore(this);
        shell = ShellBridge.get(this);

        boolean imported = false;
        try {
            imported = store.importBootstrapIfPresent();
        } catch (Exception e) {
            toast("Bootstrap import failed: " + e.getMessage());
        }

        setContentView(buildUi());
        loadFields();

        Shizuku.addRequestPermissionResultListener(permissionListener);
        requestNotificationPermission();

        if (shell.isShizukuRunning() && !shell.hasPermission()) {
            try {
                shell.requestPermission();
            } catch (Exception e) {
                toast("Shizuku permission request failed: " + e.getMessage());
            }
        } else {
            shell.ensureBound();
        }

        if (store.load().ready()) {
            startForegroundService(new Intent(this, AgentService.class));
        }

        if (imported) {
            toast("Project IR bootstrap imported");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalTaskRunner.get(this).setListener(result -> {
            if (localStatus != null) localStatus.setText(result);
        });
        handler.post(refresher);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresher);
        LocalTaskRunner.get(this).setListener(null);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        super.onDestroy();
    }

    private ScrollView buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(18);
        box.setPadding(p, p, p, p);
        scroll.addView(box, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("Project IR Mobile Agent");
        title.setTextSize(24);
        box.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Native outbound Android executor — no PC required after bootstrap.");
        box.addView(subtitle);

        TextView localTitle = label("Local Text Agent (fully on-device)");
        localTitle.setTextSize(18);
        box.addView(localTitle);
        TextView localHelp = new TextView(this);
        localHelp.setText("Commands: تنظیمات را باز کن | تنظیمات را باز کن و وارد Connections شو | ماشین حساب را باز کن | ساعت را باز کن | نقشه را باز کن | وضعیت گوشی");
        box.addView(localHelp);
        localCommand = field("Type Persian or English command");
        localCommand.setId(R.id.local_command_input);
        localCommand.setInputType(InputType.TYPE_CLASS_TEXT);
        box.addView(localCommand);
        Button localRun = button("Run locally — no Hub");
        localRun.setId(R.id.local_command_run);
        localRun.setOnClickListener(v -> {
            boolean accepted = LocalTaskRunner.get(this).execute(localCommand.getText().toString());
            if (!accepted) toast("Offline command rejected or another task is active");
        });
        box.addView(localRun);
        localStatus = new TextView(this);
        localStatus.setPadding(0, dp(6), 0, dp(12));
        localStatus.setText(LocalTaskRunner.get(this).currentStatus());
        box.addView(localStatus);

        hub = field("Hub URL");
        device = field("Device ID");
        token = field("Agent token (leave blank to keep stored token)");
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        autostart = new CheckBox(this);
        autostart.setText("Auto-start agent after boot");

        box.addView(label("Hub URL"));
        box.addView(hub);
        box.addView(label("Device ID"));
        box.addView(device);
        box.addView(label("Agent token"));
        box.addView(token);
        box.addView(autostart);

        Button save = button("Save configuration");
        save.setOnClickListener(v -> saveConfiguration());
        box.addView(save);

        Button grant = button("Grant / refresh Shizuku permission");
        grant.setOnClickListener(v -> {
            try {
                shell.requestPermission();
                shell.ensureBound();
            } catch (Exception e) {
                toast(e.getMessage());
            }
            refreshStatus();
        });
        box.addView(grant);

        Button start = button("Start Project IR agent");
        start.setOnClickListener(v -> {
            saveConfiguration();
            startForegroundService(new Intent(this, AgentService.class));
            refreshStatus();
        });
        box.addView(start);

        Button stop = button("Stop agent");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, AgentService.class));
            refreshStatus();
        });
        box.addView(stop);

        status = new TextView(this);
        status.setTextSize(14);
        status.setPadding(0, dp(18), 0, 0);
        box.addView(status);
        return scroll;
    }

    private void loadFields() {
        ConfigStore.Config config = store.load();
        hub.setText(config.hubUrl);
        device.setText(config.deviceId);
        token.setText("");
        token.setHint(store.hasStoredToken() ? "Stored in Android Keystore" : "Required");
        autostart.setChecked(config.autostart);
        refreshStatus();
    }

    private void saveConfiguration() {
        try {
            store.save(
                    hub.getText().toString(),
                    device.getText().toString(),
                    token.getText().toString(),
                    autostart.isChecked()
            );
            token.setText("");
            token.setHint("Stored in Android Keystore");
            toast("Configuration saved");
        } catch (Exception e) {
            toast("Save failed: " + e.getMessage());
        }
    }

    private void refreshStatus() {
        if (status == null) return;
        String permission;
        if (!shell.isShizukuRunning()) {
            permission = "Shizuku: not running";
        } else {
            permission = "Shizuku: running, uid=" + shell.shizukuUid()
                    + ", permission=" + (shell.hasPermission() ? "granted" : "not granted")
                    + ", user-service=" + (shell.isShellServiceReady() ? "ready" : "not ready");
        }
        ConfigStore.Config config = store.load();
        String text = "Device: " + Build.MODEL + "\n"
                + "Android: " + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT + "\n"
                + permission + "\n"
                + "Hub config: " + (config.ready() ? "ready" : "incomplete") + "\n"
                + "Agent: " + AgentService.runtimeState;
        status.setText(text);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 41002);
        }
    }

    private EditText field(String hint) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setSingleLine(true);
        return edit;
    }

    private TextView label(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setPadding(0, dp(12), 0, 0);
        return label;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
