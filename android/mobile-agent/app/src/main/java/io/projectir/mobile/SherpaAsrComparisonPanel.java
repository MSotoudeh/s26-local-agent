package io.projectir.mobile;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Experimental, isolated and fully offline A/B comparison of three proven
 * native sherpa-onnx adapters. No model switch for Side-key/Assistant.
 * No microphone, Android action execution, phone calls, or network API use.
 */
public final class SherpaAsrComparisonPanel {
    private static final String[] IDS = {
            SherpaOmniStore.MODEL_ID, SherpaWhisperStore.MODEL_ID,
            SherpaQwen3Store.MODEL_ID
    };
    private static final String[] LABELS = {
            "Omnilingual ASR 300M Int8",
            "Whisper Base multilingual Int8",
            "Qwen3-ASR 0.6B Int8"
    };
    private static final String EXPECTED_WAV_SHA =
            "4a656d803a1e07eb3707365433dccdac7cf2f055d0e0aeae2084d4f6dd255bc3";
    private SherpaAsrComparisonPanel() {}

    public static void attach(Activity activity, LinearLayout parent, LocalPersianAsr asr) {
        TextView heading = new TextView(activity);
        heading.setText("ASR A/B Lab — same public Persian WAV, CPU, offline; never Side-key");
        heading.setTextSize(18f);
        parent.addView(heading);

        Spinner engines = new Spinner(activity);
        engines.setId(R.id.m2_asr_compare_engine);
        ArrayAdapter<String> options = new ArrayAdapter<>(
                activity, android.R.layout.simple_spinner_item, LABELS);
        options.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        engines.setAdapter(options);
        parent.addView(engines);

        TextView status = new TextView(activity);
        status.setText("M2_COMPARE_IDLE | Side-key remains Vosk 0.42");
        parent.addView(status);
        TextView output = new TextView(activity);
        output.setId(R.id.m2_asr_compare_result);
        output.setText("M2_COMPARE_NO_RESULT_YET");
        output.setTextIsSelectable(true);

        Button one = new Button(activity);
        one.setId(R.id.m2_asr_compare_selected);
        one.setText("Benchmark selected ASR (public fixed WAV)");
        parent.addView(one);
        Button all = new Button(activity);
        all.setId(R.id.m2_asr_compare_all);
        all.setText("Compare all installed ASRs in one run");
        parent.addView(all);
        parent.addView(output);

        one.setOnClickListener(v ->
                run(activity, asr, engines.getSelectedItemPosition(), false,
                        one, all, status, output));
        all.setOnClickListener(v ->
                run(activity, asr, 0, true, one, all, status, output));
    }

    private static void run(Activity activity, LocalPersianAsr asr, int selected,
                            boolean all, Button one, Button allButton,
                            TextView status, TextView output) {
        AtomicBoolean gate = ModelLabBenchmarkGate.shared();
        if (selected < 0 || selected >= IDS.length || asr.isActive()
                || !gate.compareAndSet(false, true)) {
            status.setText("M2_COMPARE_DENIED_MIC_ACTIVE_OR_OTHER_MODEL_LAB_JOB");
            return;
        }
        one.setEnabled(false);
        allButton.setEnabled(false);
        status.setText("M2_COMPARE_VERIFYING_PUBLIC_WAV_AND_ALL_MODEL_SHA256");
        output.setText("M2_COMPARE_RUNNING_READ_ONLY");

        new Thread(() -> {
            String finalMessage;
            String finalStatus;
            try {
                File sample = new File(new ModelLabModelStore(activity)
                        .baselineDirectory(), "test/test.wav");
                if (!sample.isFile() || sample.length() > 5_000_000
                        || !EXPECTED_WAV_SHA.equals(sha(sample))) {
                    throw new IllegalStateException("REFERENCE_WAV_MISSING_OR_HASH_CHANGED");
                }
                JSONObject session = new JSONObject();
                session.put("schema_version", "1.0.0");
                session.put("session_type", all ? "all_three" : "one_selected");
                session.put("timestamp_unix_ms", System.currentTimeMillis());
                session.put("reference_wav_sha256", EXPECTED_WAV_SHA);
                session.put("reference_transcript", "من عرضه اين کارو ندارم");
                session.put("active_assistant_asr_at_start", asr.selectedModelId());
                session.put("runtime", "sherpa-onnx-android-v1.13.8");
                session.put("backend", "cpu");
                session.put("intent_effects", 0);
                JSONArray trials = new JSONArray();
                StringBuilder screen = new StringBuilder();
                int begin = all ? 0 : selected, end = all ? IDS.length : selected + 1;
                for (int i = begin; i < end; i++) {
                    if (asr.isActive()) throw new IllegalStateException("MIC_BECAME_ACTIVE");
                    final int index = i;
                    activity.runOnUiThread(() ->
                            status.setText("M2_COMPARE_RUNNING_" + IDS[index]));
                    JSONObject trial = new JSONObject();
                    trial.put("model_id", IDS[i]);
                    trial.put("threads", 2);
                    trial.put("resource_before", resourceSnapshot(activity));
                    Timings measured = recognize(activity, i, sample);
                    trial.put("transcript", measured.transcript);
                    trial.put("load_ms", measured.load);
                    trial.put("decode_ms", measured.decode);
                    trial.put("total_native_ms", measured.total);
                    trial.put("resource_after", resourceSnapshot(activity));
                    trial.put("status", "PASSED_NATIVE_INFERENCE");
                    trials.put(trial);
                    screen.append(LABELS[i]).append(": ")
                            .append(measured.transcript)
                            .append(" | load=").append(measured.load)
                            .append("ms decode=").append(measured.decode)
                            .append("ms total=").append(measured.total)
                            .append("ms\n");
                    // Native recognizers have been released by their respective runners.
                    if (all && i < end - 1) {
                        Thread.sleep(800);
                    }
                }
                session.put("trials", trials);
                session.put("active_assistant_asr_at_end", asr.selectedModelId());
                if (!session.getString("active_assistant_asr_at_start").equals(
                        session.getString("active_assistant_asr_at_end"))) {
                    throw new IllegalStateException("DEFAULT_ASSISTANT_ASR_CHANGED_DURING_BENCH");
                }
                String saved = save(activity, session);
                finalStatus = "M2_COMPARE_PASS_" + trials.length()
                        + "_MODELS | saved=" + saved;
                finalMessage = screen.toString() +
                        "The single public utterance cannot establish WER rankings.\n"
                        + "Receipt: " + saved;
            } catch (Throwable e) {
                finalStatus = "M2_COMPARE_ABORTED_" + e.getClass().getSimpleName();
                finalMessage = "FAIL_CLOSED: " + String.valueOf(e.getMessage())
                        + " | Production Vosk not switched";
            }
            final String message = finalMessage, state = finalStatus;
            activity.runOnUiThread(() -> {
                output.setText(message);
                status.setText(state);
                one.setEnabled(true);
                allButton.setEnabled(true);
                gate.set(false);
            });
        }, "s26-m2-asr-ab-compare").start();
    }

    private static final class Timings {
        final String transcript;
        final long load, decode, total;
        Timings(String text, long a, long b, long c) {
            transcript = text; load = a; decode = b; total = c;
        }
    }

    private static Timings recognize(Activity activity, int i, File wav)
            throws Exception {
        if (i == 0) {
            SherpaOmniStore store = new SherpaOmniStore(activity);
            SherpaOmniStore.Result gate = store.heavyPreflight();
            if (!gate.ok) throw new IllegalStateException("OMNI_" + gate.status);
            SherpaOmniRunner.Result r = SherpaOmniRunner.decodeFixedWav(
                    store.installed(), wav);
            return new Timings(r.transcript, r.loadMs, r.decodeMs, r.totalMs);
        }
        if (i == 1) {
            SherpaWhisperStore store = new SherpaWhisperStore(activity);
            SherpaWhisperStore.Result gate = store.heavyPreflight();
            if (!gate.ok) throw new IllegalStateException("WHISPER_" + gate.status);
            SherpaWhisperRunner.Result r = SherpaWhisperRunner.decodeFixedWav(
                    store.installed(), wav);
            return new Timings(r.transcript, r.loadMs, r.decodeMs, r.totalMs);
        }
        SherpaQwen3Store store = new SherpaQwen3Store(activity);
        SherpaQwen3Store.Result gate = store.heavyPreflight();
        if (!gate.ok) throw new IllegalStateException("QWEN3_" + gate.state);
        SherpaQwen3Runner.Result r = SherpaQwen3Runner.decodeFixedWav(
                store.installed(), wav);
        return new Timings(r.transcript, r.loadMs, r.decodeMs, r.totalMs);
    }

    private static JSONObject resourceSnapshot(Context context) throws Exception {
        JSONObject j = new JSONObject();
        ActivityManager manager = (ActivityManager)
                context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(info);
        j.put("available_ram_bytes", info.availMem);
        j.put("android_low_memory", info.lowMemory);
        PowerManager power = (PowerManager)
                context.getSystemService(Context.POWER_SERVICE);
        j.put("thermal_status", power == null ? -1 : power.getCurrentThermalStatus());
        BatteryManager battery = (BatteryManager)
                context.getSystemService(Context.BATTERY_SERVICE);
        j.put("battery_percent", battery == null ? -1 :
                battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
        return j;
    }

    private static String sha(File path) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(path)) {
            byte[] buf = new byte[1048576];
            int read;
            while ((read = in.read(buf)) != -1) if (read > 0) hash.update(buf, 0, read);
        }
        StringBuilder b = new StringBuilder();
        for (byte v : hash.digest()) {
            b.append(Character.forDigit((v >> 4) & 15, 16));
            b.append(Character.forDigit(v & 15, 16));
        }
        return b.toString();
    }

    private static String save(Activity activity, JSONObject receipt)
            throws Exception {
        File root = activity.getExternalFilesDir("benchmarks");
        if (root == null || (!root.isDirectory() && !root.mkdirs())) {
            throw new IllegalStateException("RECEIPT_STORAGE_UNAVAILABLE");
        }
        String name = "m2-asr-compare-" + System.currentTimeMillis() + "-"
                + (System.nanoTime() & 0xFFFF) + ".json";
        File target = new File(root, name);
        File staging = new File(root, name + ".partial");
        if (target.exists() || staging.exists()) throw new IllegalStateException("RECEIPT_FILE_COLLISION");
        byte[] raw = (receipt.toString(2) + "\n").getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream stream = new FileOutputStream(staging, false)) {
            stream.write(raw);
            stream.getFD().sync();
        }
        if (!staging.renameTo(target)) throw new IllegalStateException("RECEIPT_ATOMIC_RENAME_FAILED");
        return name;
    }
}
