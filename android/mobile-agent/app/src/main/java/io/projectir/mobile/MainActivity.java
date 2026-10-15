package io.projectir.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.role.RoleManager;
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
    private EditText localActionId;
    private TextView localStatus;
    private TextView planStatus;
    private TextView voiceStatus;
    private TextView llmStatus;
    private LocalPersianAsr persianAsr;
    private LocalLlmEngine llmEngine;
    private TextView assistantVoiceBanner;
    private boolean activityResumed;
    private boolean pendingAssistGesture;
    private boolean assistantMicrophoneActive;
    private boolean assistTranscriptHandled;
    private int voiceSession;
    private static final String SKIP_MIC_TEST_EXTRA =
            "io.projectir.mobile.extra.SKIP_ASSIST_MIC";

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
        persianAsr = new LocalPersianAsr(this);
        llmEngine = new LocalLlmEngine(this);
        // The ACTION_ASSIST launch is a deliberate foreground user gesture.
        // Never auto-open a microphone during MAIN/launcher launches or tests.
        pendingAssistGesture = isUserAssistIntent(getIntent());

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
        activityResumed = true;
        LocalTaskRunner.get(this).setListener(result -> {
            if (localStatus != null) localStatus.setText(result);
        });
        LocalAndroidPlanRuntime.get(this).setListener(result -> {
            if (planStatus != null) planStatus.setText(result);
        });
        handler.post(refresher);
        maybeStartAssistGesture();
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        pendingAssistGesture = false;
        ++voiceSession;
        assistantMicrophoneActive = false;
        handler.removeCallbacks(refresher);
        // A microphone session may not continue invisibly in the background.
        if (persianAsr != null) persianAsr.stop();
        LocalTaskRunner.get(this).setListener(null);
        LocalAndroidPlanRuntime.get(this).setListener(null);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        if (persianAsr != null) persianAsr.release();
        if (llmEngine != null) llmEngine.release();
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
        assistantVoiceBanner = new TextView(this);
        assistantVoiceBanner.setTextSize(17);
        assistantVoiceBanner.setPadding(0, dp(12), 0, dp(8));
        assistantVoiceBanner.setText(
                "ASSIST_READY: Hold Side key to listen in Persian offline. "
                + "Verified navigation: Settings / Connections; questions: Qwen text only.");
        box.addView(assistantVoiceBanner);
        Button cancelAssistantVoice = button("Stop Assistant listening");
        cancelAssistantVoice.setOnClickListener(v -> stopAssistantListening());
        box.addView(cancelAssistantVoice);

        TextView localTitle = label("Local Text Agent (fully on-device)");
        localTitle.setTextSize(18);
        box.addView(localTitle);
        TextView localHelp = new TextView(this);
        localHelp.setText("Commands: تنظیمات را باز کن | تنظیمات را باز کن و وارد Connections شو | ماشین حساب را باز کن | ساعت را باز کن | نقشه را باز کن | وضعیت گوشی | پنج ثانیه صبر کن (برای Cancel)");
        box.addView(localHelp);
        localCommand = field("Type Persian or English command");
        localCommand.setId(R.id.local_command_input);
        localCommand.setInputType(InputType.TYPE_CLASS_TEXT);
        box.addView(localCommand);
        localActionId = field("Action ID (optional, for retry/deduplication)");
        localActionId.setId(R.id.local_action_id_input);
        localActionId.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        box.addView(localActionId);
        Button localRun = button("Run locally — no Hub");
        localRun.setId(R.id.local_command_run);
        localRun.setOnClickListener(v -> {
            String id = localActionId.getText().toString().trim();
            boolean accepted = id.isEmpty()
                    ? LocalTaskRunner.get(this).execute(localCommand.getText().toString())
                    : LocalTaskRunner.get(this).execute(localCommand.getText().toString(), id);
            if (!accepted) toast("Offline command rejected, repeated, or another task is active");
        });
        box.addView(localRun);
        Button localCancel = button("Cancel local task");
        localCancel.setId(R.id.local_command_cancel);
        localCancel.setOnClickListener(v -> {
            if (!LocalTaskRunner.get(this).cancel()) toast("No active local task");
        });
        box.addView(localCancel);
        localStatus = new TextView(this);
        localStatus.setPadding(0, dp(6), 0, dp(12));
        localStatus.setText(LocalTaskRunner.get(this).currentStatus());
        box.addView(localStatus);
        Button previewPlan = button("Preview local skill — no action");
        previewPlan.setId(R.id.local_plan_preview);
        previewPlan.setOnClickListener(v -> {
            LocalSkillPlan plan = LocalSkillRegistry.lookup(localCommand.getText().toString());
            if (plan == null) plan = LocalSkillRegistry.emptyPreview();
            String rejection = LocalPlanPolicy.rejection(plan);
            localStatus.setText(plan.preview() + (rejection == null
                    ? "PLAN SHAPE: VALID"
                    : "PLAN SHAPE: " + rejection));
        });
        box.addView(previewPlan);

        Button runVerifiedPlan = button("Run verified multi-step plan (offline)");
        runVerifiedPlan.setOnClickListener(v -> {
            String id = localActionId.getText().toString().trim();
            if (id.isEmpty()) id = java.util.UUID.randomUUID().toString();
            if (!LocalAndroidPlanRuntime.get(this).start(
                    localCommand.getText().toString(), id)) {
                toast("Plan rejected: verify Accessibility, policy and task ID");
            }
        });
        box.addView(runVerifiedPlan);
        Button cancelVerifiedPlan = button("Cancel multi-step plan");
        cancelVerifiedPlan.setOnClickListener(v -> {
            if (!LocalAndroidPlanRuntime.get(this).cancel()) {
                toast("No active multi-step plan");
            }
        });
        box.addView(cancelVerifiedPlan);
        planStatus = new TextView(this);
        planStatus.setText(LocalAndroidPlanRuntime.get(this).status());
        box.addView(planStatus);

        TextView faTitle = label("Persian speech - Vosk on-device only (push to talk)");
        faTitle.setTextSize(18);
        box.addView(faTitle);
        voiceStatus = new TextView(this);
        voiceStatus.setText("FA_IR_ASR_MODEL=" + (persianAsr.modelInstalled() ? "INSTALLED" : "MISSING")
                + " (microphone starts only from Side-key Assist or explicit Listen)");
        box.addView(voiceStatus);
        Button listenPersian = button("Listen Persian offline (tap to start)");
        listenPersian.setOnClickListener(v -> {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 4102);
                voiceStatus.setText("Grant mic permission, then tap Listen again");
                return;
            }
            persianAsr.begin(new LocalPersianAsr.Listener() {
                @Override public void onStatus(String message) {
                    voiceStatus.setText(message);
                }
                @Override public void onTranscript(String transcript) {
                    // A transcript is merely a DRAFT, not an action.
                    localCommand.setText(transcript);
                    localCommand.setSelection(localCommand.length());
                    voiceStatus.setText("FA_IR_DRAFT_READY_REVIEW_BEFORE_RUN");
                }
            });
        });
        box.addView(listenPersian);
        Button stopPersian = button("Stop Persian microphone");
        stopPersian.setOnClickListener(v -> {
            persianAsr.stop();
            voiceStatus.setText("ASR_STOP_REQUESTED");
        });
        box.addView(stopPersian);
        Button verifyPersian = button("Test offline Persian sample (no microphone)");
        verifyPersian.setOnClickListener(v -> {
            boolean started = persianAsr.evaluateBundledSample(new LocalPersianAsr.Listener() {
                @Override public void onStatus(String message) {
                    voiceStatus.setText(message);
                }
                @Override public void onTranscript(String transcript) {
                    // This is public bundled test audio. No action is dispatched.
                    localCommand.setText(transcript);
                    voiceStatus.setText("ASR_BUNDLED_SAMPLE_TRANSCRIBED");
                }
            });
            if (!started) voiceStatus.setText("ASR_SAMPLE_MODEL_OR_SESSION_UNAVAILABLE");
        });
        box.addView(verifyPersian);

        TextView modelTitle = label("Local language model - text responses only");
        modelTitle.setTextSize(18);
        box.addView(modelTitle);
        llmStatus = new TextView(this);
        llmStatus.setText("QWEN_LOCAL_MODEL=" + (llmEngine.ready() ? "INSTALLED" : "MISSING")
                + " (never authorized to execute device actions)");
        box.addView(llmStatus);
        Button queryLocalLlm = button("Ask Qwen locally - show response, no actions");
        queryLocalLlm.setOnClickListener(v -> {
            llmStatus.setText("LOCAL_LLM_LOADING");
            boolean started = llmEngine.generate(localCommand.getText().toString(),
                    answer -> llmStatus.setText(answer));
            if (!started) llmStatus.setText("LOCAL_LLM_NOT_READY_OR_BUSY");
        });
        box.addView(queryLocalLlm);

        TextView assistantTitle = label("Android Assistant role (owner opt-in)");
        assistantTitle.setTextSize(18);
        box.addView(assistantTitle);
        TextView assistantStatus = new TextView(this);
        RoleManager roles = getSystemService(RoleManager.class);
        assistantStatus.setText(roles == null || !roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT)
                ? "ASSISTANT_ROLE_UNAVAILABLE"
                : roles.isRoleHeld(RoleManager.ROLE_ASSISTANT)
                    ? "ASSISTANT_ROLE_ALREADY_HELD"
                    : "ASSISTANT_ROLE_NOT_HELD (current default unchanged)");
        box.addView(assistantStatus);
        Button requestAssistant = button("Request Android Assistant role (system confirmation)");
        requestAssistant.setOnClickListener(v -> {
            RoleManager manager = getSystemService(RoleManager.class);
            if (manager == null || !manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                assistantStatus.setText("ASSISTANT_ROLE_UNAVAILABLE");
                return;
            }
            if (manager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
                assistantStatus.setText("ASSISTANT_ROLE_ALREADY_HELD");
                return;
            }
            startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), 4103);
        });
        box.addView(requestAssistant);

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
        ModelLabPanel.attach(this, box, persianAsr);
        return scroll;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        pendingAssistGesture = isUserAssistIntent(intent);
        maybeStartAssistGesture();
    }

    private static boolean isUserAssistIntent(Intent intent) {
        return intent != null && Intent.ACTION_ASSIST.equals(intent.getAction())
                && !intent.getBooleanExtra(SKIP_MIC_TEST_EXTRA, false);
    }

    private void maybeStartAssistGesture() {
        if (!activityResumed || !pendingAssistGesture) return;
        pendingAssistGesture = false; // intent consumed at most once
        final int ticket = ++voiceSession;
        handler.postDelayed(() -> {
            if (ticket == voiceSession && activityResumed) {
                beginAssistListening(ticket);
            }
        }, 350L);
    }

    private void beginAssistListening(int ticket) {
        if (!activityResumed || assistantVoiceBanner == null) return;
        RoleManager role = getSystemService(RoleManager.class);
        if (role == null || !role.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
            assistantVoiceBanner.setText("ASSIST_REJECTED_NOT_DEFAULT_ASSISTANT");
            return;
        }
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (keyguard != null && keyguard.isKeyguardLocked()) {
            assistantVoiceBanner.setText("ASSIST_REJECTED_LOCKED_DEVICE");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            assistantVoiceBanner.setText(
                    "ASSIST_NEEDS_MIC_PERMISSION: use Listen Persian offline to grant");
            return;
        }
        if (!persianAsr.modelInstalled()) {
            assistantVoiceBanner.setText("ASSIST_PERSIAN_MODEL_MISSING");
            return;
        }
        if (LocalAndroidPlanRuntime.get(this).busy()
                || LocalTaskRunner.get(this).isBusy()) {
            assistantVoiceBanner.setText("ASSIST_REJECTED_OTHER_TASK_ACTIVE");
            return;
        }
        // Explicitly foreground only: no hotword, passive recording or
        // background service is used for microphone access.
        if (persianAsr.isActive()) persianAsr.stop();
        assistTranscriptHandled = false;
        assistantMicrophoneActive = true;
        localCommand.setText("");
        assistantVoiceBanner.setText("ASSIST_LISTENING_FA_IR_OFFLINE: speak now");
        persianAsr.begin(new LocalPersianAsr.Listener() {
            @Override public void onStatus(String state) {
                handler.post(() -> {
                    if (ticket != voiceSession || !activityResumed
                            || !assistantMicrophoneActive) return;
                    if (state.startsWith("ASR_MODEL_ERROR")
                            || state.startsWith("ASR_START_FAILED")
                            || "ASR_BUSY".equals(state) || "ASR_MIC_ERROR".equals(state)
                            || "ASR_TIMEOUT".equals(state)
                            || "ASR_MODEL_NOT_INSTALLED".equals(state)
                            || "ASR_STOPPED".equals(state)) {
                        assistantMicrophoneActive = false;
                        assistantVoiceBanner.setText("ASSIST_" + state);
                    } else {
                        assistantVoiceBanner.setText("ASSIST_" + state);
                    }
                });
            }
            @Override public void onTranscript(String transcript) {
                handler.post(() -> acceptAssistTranscript(ticket, transcript));
            }
        });
    }

    private void stopAssistantListening() {
        ++voiceSession;
        pendingAssistGesture = false;
        assistantMicrophoneActive = false;
        assistTranscriptHandled = true;
        if (persianAsr != null) persianAsr.stop();
        if (assistantVoiceBanner != null) {
            assistantVoiceBanner.setText("ASSIST_LISTENING_CANCELLED");
        }
    }

    private void acceptAssistTranscript(int ticket, String transcript) {
        if (ticket != voiceSession || !activityResumed || !assistantMicrophoneActive
                || assistTranscriptHandled) return;
        // Vosk may deliver onResult AND onFinalResult. At most one
        // transcript from a physical Side-key session is acted upon.
        assistTranscriptHandled = true;
        assistantMicrophoneActive = false;
        persianAsr.stop();
        localCommand.setText(transcript);
        localCommand.setSelection(localCommand.length());
        LocalVoiceRouting.Decision decision = LocalVoiceRouting.classify(transcript);
        if (decision.kind == LocalVoiceRouting.Kind.NAVIGATE) {
            LocalSkillPlan canonical = LocalSkillRegistry.canonicalById(decision.skillId);
            if (canonical == null || !LocalPlanPolicy.mayExecute(canonical)
                    || !decision.skillId.equals(canonical.skillId)
                    || LocalAndroidPlanRuntime.get(this).busy()
                    || LocalTaskRunner.get(this).isBusy()) {
                assistantVoiceBanner.setText("ASSIST_NAVIGATION_REJECTED_BY_POLICY");
                return;
            }
            // Unique ID: a previous spoken action is never silently replayed.
            String id = "assist_" + java.util.UUID.randomUUID();
            localActionId.setText(id);
            assistantVoiceBanner.setText(
                    "ASSIST_VERIFIED_R0_START: " + decision.skillId);
            boolean accepted = LocalAndroidPlanRuntime.get(this).start(
                    decision.canonicalCommand, id);
            if (!accepted) {
                assistantVoiceBanner.setText("ASSIST_PLAN_REJECTED_OR_ACCESSIBILITY_MISSING");
            }
        } else if (decision.kind == LocalVoiceRouting.Kind.ANSWER) {
            assistantVoiceBanner.setText("ASSIST_QWEN_LOCAL_TEXT_ONLY_LOADING");
            boolean accepted = llmEngine.generate(transcript, answer -> {
                if (ticket != voiceSession || !activityResumed) return;
                llmStatus.setText(answer);
                assistantVoiceBanner.setText("ASSIST_QWEN_TEXT_RESULT_READY: "
                        + answer.substring(0, Math.min(130, answer.length())));
            });
            if (!accepted) {
                assistantVoiceBanner.setText("ASSIST_QWEN_UNAVAILABLE_OR_BUSY");
            }
        } else {
            assistantVoiceBanner.setText(
                    "ASSIST_UNSUPPORTED_SPEECH: no device action; check/edit draft");
        }
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
