package io.s26.pstnlab;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import rikka.shizuku.Shizuku;

/**
 * Independent owner-facing app; does not change Project IR or default dialer.
 * Binds real Shizuku UserService, then runs only fake PCM dry-run.
 */
public final class MainActivity extends Activity {
    private static final int PERMISSION_REQUEST = 20261013;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView output;
    private boolean pending;
    private boolean bound;
    private ILabService service;
    private Shizuku.UserServiceArgs args;
    private final Shizuku.OnBinderReceivedListener binderReceived = this::attemptBind;
    private final Shizuku.OnBinderDeadListener binderDead = () -> {
        service = null;
        bound = false;
        pending = false;
        publish("SHIZUKU_SERVER_DISCONNECTED");
    };
    private final Shizuku.OnRequestPermissionResultListener permissionListener = (code, status) -> {
        if (code == PERMISSION_REQUEST) {
            publish("PERMISSION_RESULT=" + (status == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED"));
            if (status == PackageManager.PERMISSION_GRANTED) attemptBind();
        }
    };
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ILabService.Stub.asInterface(binder);
            pending = false;
            bound = true;
            publish("SERVICE_CONNECTED");
            runDryRun();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service = null; bound = false; pending = false;
            publish("SERVICE_DISCONNECTED");
        }
        @Override public void onBindingDied(ComponentName name) {
            service = null; bound = false; pending = false;
            publish("SERVICE_BINDING_DIED");
        }
        @Override public void onNullBinding(ComponentName name) {
            service = null; bound = false; pending = false;
            publish("SERVICE_NULL_BINDING");
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        column.setPadding(pad, pad, pad, pad);
        TextView heading = new TextView(this);
        heading.setText("S26 PSTN Audio Lab (NO REAL CALL AUDIO)");
        heading.setTextSize(18);
        column.addView(heading);
        Button bindButton = new Button(this);
        bindButton.setText("Bind Shizuku service / Grant permission");
        bindButton.setOnClickListener(v -> { if (hasAccess()) attemptBind(); else requestAccess(); });
        column.addView(bindButton);
        Button testButton = new Button(this);
        testButton.setText("Run FAKE PCM (100 frames)");
        testButton.setOnClickListener(v -> runDryRun());
        column.addView(testButton);
        Button stopButton = new Button(this);
        stopButton.setText("Stop this lab UserService");
        stopButton.setOnClickListener(v -> stopLab());
        column.addView(stopButton);
        output = new TextView(this);
        output.setTextIsSelectable(true);
        output.setTextSize(13);
        column.addView(output);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(column);
        setContentView(scroll);
        args = new Shizuku.UserServiceArgs(new ComponentName(this, LabUserService.class))
                .processNameSuffix("s26_pstn_lab").daemon(true).version(2);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        Shizuku.addBinderReceivedListenerSticky(binderReceived);
        Shizuku.addBinderDeadListener(binderDead);
        publish("ACTIVITY_CREATED");
        if (hasAccess()) attemptBind(); else publish("PERMISSION_REQUIRED_TAP_BIND");
    }

    private boolean hasAccess() {
        try { return Shizuku.pingBinder() && Shizuku.getUid() == 2000
                && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; }
        catch (Throwable e) { return false; }
    }
    private void requestAccess() {
        try { Shizuku.requestPermission(PERMISSION_REQUEST); }
        catch (Throwable e) { publish("PERMISSION_ERROR=" + e.getClass().getSimpleName()); }
    }
    private void attemptBind() {
        if (Looper.myLooper() != Looper.getMainLooper()) { ui.post(this::attemptBind); return; }
        if (bound || pending || !hasAccess()) return;
        pending = true;
        publish("BIND_REQUESTED");
        try { Shizuku.bindUserService(args, connection); }
        catch (Throwable e) {
            pending = false;
            publish("BIND_ERROR=" + e.getClass().getSimpleName());
        }
    }
    private void runDryRun() {
        if (service == null) { publish("NO_SERVICE_FOR_TEST"); return; }
        try {
            JSONObject state = new JSONObject(service.status());
            JSONObject fake = new JSONObject(service.fakePcmTest());
            int binderFrames = 0;
            for (int index = 0; index < 100; index++) {
                byte[] pcm = new byte[640];
                for (int j = 0; j < pcm.length; j++) pcm[j] = (byte)((index + j) & 0xff);
                byte[] response = service.fakePcmFrame(pcm);
                if (!java.util.Arrays.equals(pcm, response)) throw new IllegalStateException("PCM Binder mismatch at frame " + index);
                binderFrames++;
            }
            JSONObject report = new JSONObject();
            report.put("binder_pcm_20ms_frames", binderFrames);
            report.put("scope", "NO_CALL_NO_REAL_AUDIO");
            report.put("service", state);
            report.put("fake_pcm", fake);
            report.put("pass", state.optBoolean("shell") && fake.optBoolean("pass")
                    && "FAKE_ONLY".equals(fake.optString("backend")) && binderFrames == 100);
            writeReport(report);
            publish("TEST_RESULT=" + (report.getBoolean("pass") ? "PASS" : "FAIL")
                    + " RX=" + fake.optInt("rx") + " TX=" + fake.optInt("tx") + " BINDER=" + binderFrames
                    + " UID=" + state.optInt("uid"));
        } catch (Throwable e) {
            publish("TEST_ERROR=" + e.getClass().getSimpleName());
        }
    }
    private void writeReport(JSONObject report) throws Exception {
        try (FileOutputStream out = openFileOutput("lab-status.json", Context.MODE_PRIVATE)) {
            out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }
    private void stopLab() {
        if (args == null) return;
        try {
            if (service != null) service.destroy();
        } catch (Throwable ignored) { }
        try { Shizuku.unbindUserService(args, connection, true); }
        catch (Throwable ignored) { }
        pending = false; bound = false; service = null;
        publish("LAB_SERVICE_STOPPED");
    }
    private void publish(String text) {
        ui.post(() -> {
            if (output != null) output.append(text + "\n");
            android.util.Log.i("S26PstnLab", text);
        });
    }
    @Override protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        Shizuku.removeBinderReceivedListener(binderReceived);
        Shizuku.removeBinderDeadListener(binderDead);
        // Do not destroy Shizuku UserService here: daemon survives UI process.
        super.onDestroy();
    }
}
