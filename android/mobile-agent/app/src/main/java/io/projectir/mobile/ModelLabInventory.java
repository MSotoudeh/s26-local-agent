package io.projectir.mobile;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Device-side read-only Model Lab inventory.
 * Catalog status "planned" never implies a usable adapter or installed model.
 * Actual eligibility is confirmed using installed model files and runtime.
 */
public final class ModelLabInventory {
    public static final class Entry {
        public final String id;
        public final String task;
        public final String sourceStatus;
        public final String license;
        public final String tier;
        public final String[] runtimes;
        public final boolean availableNow;
        Entry(String id, String task, String sourceStatus, String license,
              String tier, String[] runtimes, boolean availableNow) {
            this.id = id;
            this.task = task;
            this.sourceStatus = sourceStatus;
            this.license = license;
            this.tier = tier;
            this.runtimes = runtimes;
            this.availableNow = availableNow;
        }
        public boolean matches(String filter) {
            for (String name : task.split(",")) {
                if (filter.equals(name)) return true;
            }
            return false;
        }
        public String describe() {
            return (availableNow
                    ? (ModelLabModelStore.CANDIDATE_ID.equals(id)
                        ? "[FILES PRESENT, VERIFY ON SWITCH] " : "[READY] ")
                    : "[NOT INSTALLED] ") + id
                    + " [" + tier + "; " + sourceStatus + "]";
        }
    }
    private ModelLabInventory() {}

    public static List<Entry> read(Context context) throws Exception {
        final String data;
        try (InputStream stream = context.getAssets().open("s26_model_lab_catalog.json")) {
            byte[] bytes = new byte[stream.available()];
            int off = 0;
            while (off < bytes.length) {
                int len = stream.read(bytes, off, bytes.length - off);
                if (len < 0) throw new IllegalStateException("CATALOG_TRUNCATED");
                off += len;
            }
            data = new String(bytes, StandardCharsets.UTF_8);
        }
        JSONObject catalog = new JSONObject(data);
        if (!"1.0.0".equals(catalog.optString("schema_version"))) {
            throw new IllegalArgumentException("CATALOG_SCHEMA_MISMATCH");
        }
        JSONArray entries = catalog.getJSONArray("entries");
        List<Entry> result = new ArrayList<>();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject e = entries.getJSONObject(i);
            String id = e.getString("id");
            String task = e.getString("task");
            String status = e.getString("status");
            JSONArray arr = e.getJSONArray("runtimes");
            String[] runtimes = new String[arr.length()];
            for (int j = 0; j < arr.length(); j++) runtimes[j] = arr.getString(j);
            result.add(new Entry(id, task, status, e.getString("license"),
                    e.getString("tier"), runtimes,
                    ("installed_verified".equals(status)
                            || ModelLabModelStore.CANDIDATE_ID.equals(id))
                            && physicallyAvailable(context, id)));
        }
        return Collections.unmodifiableList(result);
    }

    private static boolean physicallyAvailable(Context ctx, String id) {
        if ("vision.accessibility_tree".equals(id)) {
            return UiAccessibilityService.instance() != null;
        }
        File models = ctx.getExternalFilesDir("models");
        if (models == null) return false;
        if (ModelLabModelStore.CANDIDATE_ID.equals(id)) {
            return new ModelLabModelStore(ctx).candidatePresent();
        }
        if ("asr.vosk.fa-small-042".equals(id)) {
            File path = new File(models, "vosk-model-small-fa-0.42");
            return new File(path, "am/final.mdl").isFile()
                    && new File(path, "conf/model.conf").isFile();
        }
        if ("llm.qwen2_5.0_5b.int8".equals(id)) {
            File artifact = new File(models, LocalLlmEngine.MODEL_FILE);
            return artifact.isFile() && artifact.length() > 400_000_000L;
        }
        // Future runtimes must implement their own strict artifact/probe
        // checks, never infer availability from a filename or catalog claim.
        return false;
    }
}
