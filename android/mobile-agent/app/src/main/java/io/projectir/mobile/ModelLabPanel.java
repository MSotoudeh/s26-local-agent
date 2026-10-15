package io.projectir.mobile;

import android.app.Activity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Early M1 on-device model catalogue UI. Selection is PREVIEW-ONLY until
 * another runtime adapter has passed native load and health checks.
 * No dummy "activate" action or fake installed status.
 */
public final class ModelLabPanel {
    private ModelLabPanel() {}

    public static void attach(Activity activity, LinearLayout parent, LocalPersianAsr asr) {
        TextView title = new TextView(activity);
        title.setText("Model Lab: staged verified Vosk install + real ASR switching");
        title.setTextSize(18);
        parent.addView(title);
        TextView status = new TextView(activity);
        status.setText("MODEL_LAB_CATALOG_LOADING");
        parent.addView(status);
        Spinner tasks = new Spinner(activity);
        tasks.setId(R.id.model_lab_task_spinner);
        String[] kinds = {"ASR", "LLM", "TTS", "VAD", "VLM", "Vision"};
        ArrayAdapter<String> taskAdapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item, kinds);
        taskAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        tasks.setAdapter(taskAdapter);
        parent.addView(tasks, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Spinner models = new Spinner(activity);
        models.setId(R.id.model_lab_model_spinner);
        parent.addView(models, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final List<ModelLabInventory.Entry> catalog;
        try {
            catalog = ModelLabInventory.read(activity);
            status.setText("MODEL_LAB_CATALOG_ENTRIES=" + catalog.size()
                    + " | READY depends on installed weight + functioning adapter");
        } catch (Exception e) {
            status.setText("MODEL_LAB_CATALOG_ERROR=" + e.getClass().getSimpleName());
            return;
        }
        final List<ModelLabInventory.Entry> selection = new ArrayList<>();
        Runnable populate = () -> {
            String filter = kinds[tasks.getSelectedItemPosition()].toLowerCase(java.util.Locale.ROOT);
            selection.clear();
            ArrayList<String> descriptions = new ArrayList<>();
            for (ModelLabInventory.Entry entry : catalog) {
                if (entry.matches(filter)) {
                    selection.add(entry);
                    if (ModelLabModelStore.CANDIDATE_ID.equals(entry.id)
                        && new ModelLabModelStore(activity).candidatePresent()) {
                    descriptions.add("[FILES PRESENT, VERIFY ON SWITCH] " + entry.id
                            + " [balanced; SHA256 manifest]");
                } else {
                    descriptions.add(entry.describe());
                }
                }
            }
            ArrayAdapter<String> modelAdapter = new ArrayAdapter<>(activity,
                    android.R.layout.simple_spinner_item, descriptions);
            modelAdapter.setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item);
            models.setAdapter(modelAdapter);
        };
        tasks.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,
                    android.view.View view, int position, long id) {
                populate.run();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {
                selection.clear();
            }
        });
        populate.run();
        Button inspect = new Button(activity);
        inspect.setText("Inspect selected Model Lab candidate (read only)");
        inspect.setOnClickListener(v -> {
            int pos = models.getSelectedItemPosition();
            if (pos < 0 || pos >= selection.size()) {
                status.setText("MODEL_LAB_NO_SELECTION");
                return;
            }
            ModelLabInventory.Entry item = selection.get(pos);
            status.setText("MODEL_LAB_MODEL=" + item.id
                    + " | status=" + (item.availableNow ? "VERIFIED_BASELINE" : "NOT_READY")
                    + " | license=" + item.license
                    + " | runtimes=" + String.join(",", item.runtimes)
                    + " | switch=NOT_YET_ENABLED_UNTIL_ADAPTER_GATE");
        });
        parent.addView(inspect);

        ModelLabModelStore store = new ModelLabModelStore(activity);
        Button install = new Button(activity);
        install.setId(R.id.model_lab_install_staged_vosk);
        install.setText("Verify and install staged Vosk Persian 0.5");
        install.setOnClickListener(v -> {
            if (asr.isActive()) {
                status.setText("MODEL_LAB_INSTALL_DENIED_ASR_BUSY");
                return;
            }
            install.setEnabled(false);
            status.setText("MODEL_LAB_INSTALL_VERIFYING_SHA256_BACKGROUND");
            new Thread(() -> {
                ModelLabModelStore.Receipt receipt = store.installStagedCandidate();
                activity.runOnUiThread(() -> {
                    install.setEnabled(true);
                    if (receipt.ok) populate.run();
                    status.setText("MODEL_LAB_INSTALL_" + receipt.state
                            + " | files=" + receipt.files + " | bytes=" + receipt.bytes);
                });
            }, "model-lab-stage-verify").start();
        });
        parent.addView(install);

        TextView active = new TextView(activity);
        active.setText("ASR_SELECTED_MODEL=" + asr.selectedModelId());
        Button activate = new Button(activity);
        activate.setId(R.id.model_lab_activate_vosk);
        activate.setText("Activate selected Vosk ASR model (verified only)");
        activate.setOnClickListener(v -> {
            int pos = models.getSelectedItemPosition();
            if (!"ASR".equals(kinds[tasks.getSelectedItemPosition()])
                    || pos < 0 || pos >= selection.size()) {
                status.setText("MODEL_LAB_SWITCH_SELECT_ASR_FIRST");
                return;
            }
            String selected = selection.get(pos).id;
            if (!ModelLabModelStore.BASELINE_ID.equals(selected)
                    && !ModelLabModelStore.CANDIDATE_ID.equals(selected)) {
                status.setText("MODEL_LAB_SWITCH_NO_AVAILABLE_ADAPTER");
                return;
            }
            activate.setEnabled(false);
            boolean accepted = asr.switchModel(selected, new LocalPersianAsr.Listener() {
                @Override public void onStatus(String message) {
                    status.setText("MODEL_LAB_" + message);
                    active.setText("ASR_SELECTED_MODEL=" + asr.selectedModelId());
                    if (message.startsWith("ASR_SWITCH_OK_")
                            || message.startsWith("ASR_SWITCH_ROLLED_BACK_")
                            || message.startsWith("ASR_SWITCH_ROLLBACK_FAILED_")
                            || message.startsWith("ASR_SWITCH_ALREADY_ACTIVE_")
                            || message.startsWith("ASR_SWITCH_BUSY")
                            || message.startsWith("ASR_SWITCH_UNSUPPORTED_")) {
                        activate.setEnabled(true);
                    }
                }
                @Override public void onTranscript(String text) { /* no mic during switch */ }
            });
            if (!accepted) activate.setEnabled(true);
        });
        parent.addView(activate);
        parent.addView(active);
        // Independent M2 engine benchmark. No implicit change to Assistant ASR.
        SherpaOmniPanel.attach(activity, parent, asr);
        // Second sherpa backend target: pinned multilingual Whisper-base Int8.
        SherpaWhisperPanel.attach(activity, parent, asr);
        SherpaQwen3Panel.attach(activity, parent, asr);
    }
}
