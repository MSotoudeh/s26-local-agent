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

    public static void attach(Activity activity, LinearLayout parent) {
        TextView title = new TextView(activity);
        title.setText("Model Lab (catalog + verified baseline; alternative runtimes pending)");
        title.setTextSize(18);
        parent.addView(title);
        TextView status = new TextView(activity);
        status.setText("MODEL_LAB_CATALOG_LOADING");
        parent.addView(status);
        Spinner tasks = new Spinner(activity);
        String[] kinds = {"ASR", "LLM", "TTS", "VAD", "VLM", "Vision"};
        ArrayAdapter<String> taskAdapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item, kinds);
        taskAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        tasks.setAdapter(taskAdapter);
        parent.addView(tasks, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Spinner models = new Spinner(activity);
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
                    descriptions.add(entry.describe());
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
    }
}
