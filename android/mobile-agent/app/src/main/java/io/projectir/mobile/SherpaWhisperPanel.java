package io.projectir.mobile;

import android.app.Activity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * M2 experimental sherpa-onnx panel; first alternative ASR engine.
 * This is a read-only/transcription-only lab, not a default Assistant switch:
 * result text cannot be routed to Android actions.
 */
public final class SherpaWhisperPanel {
    private SherpaWhisperPanel() {}

    public static void attach(Activity activity, LinearLayout parent, LocalPersianAsr asr) {
        SherpaWhisperStore store = new SherpaWhisperStore(activity);
        TextView title = new TextView(activity);
        title.setText("M2 sherpa-onnx Whisper base multilingual Int8 (independent ASR lab)");
        title.setTextSize(18f);
        parent.addView(title);
        TextView status = new TextView(activity);
        status.setText("M2_WHISPER_NOT_ACTIVE | pending SHA256-verified installation");
        parent.addView(status);
        TextView result = new TextView(activity);
        result.setText("M2_WHISPER_NO_BENCHMARK_YET");
        result.setTextIsSelectable(true);
        AtomicBoolean busy = ModelLabBenchmarkGate.shared();

        Button install = new Button(activity);
        install.setId(R.id.m2_whisper_install);
        install.setText("Verify & install staged Whisper base (offline)");
        parent.addView(install);
        Button run = new Button(activity);
        run.setId(R.id.m2_whisper_benchmark);
        run.setText("Benchmark Whisper on SAME bundled WAV (no microphone)");
        parent.addView(run);

        install.setOnClickListener(view -> {
            if (asr.isActive() || !busy.compareAndSet(false, true)) {
                status.setText("M2_WHISPER_OPERATION_BUSY_OR_MIC_ACTIVE");
                return;
            }
            install.setEnabled(false);
            run.setEnabled(false);
            status.setText("M2_WHISPER_INSTALL_VERIFYING_FILE_SHA256");
            new Thread(() -> {
                SherpaWhisperStore.Result done = store.installVerifiedStage();
                activity.runOnUiThread(() -> {
                    busy.set(false);
                    install.setEnabled(true);
                    run.setEnabled(true);
                    status.setText("M2_WHISPER_INSTALL_" + done.status
                            + " | files=" + done.files + " bytes=" + done.bytes);
                });
            }, "s26-m2-omni-verify-stage").start();
        });

        run.setOnClickListener(view -> {
            if (asr.isActive() || !busy.compareAndSet(false, true)) {
                status.setText("M2_WHISPER_BENCH_BUSY_OR_MIC_ACTIVE");
                return;
            }
            install.setEnabled(false);
            run.setEnabled(false);
            status.setText("M2_WHISPER_PRECHECK_RUNNING");
            new Thread(() -> {
                SherpaWhisperStore.Result gate = store.heavyPreflight();
                if (!gate.ok) {
                    activity.runOnUiThread(() -> {
                        status.setText("M2_WHISPER_BENCH_GATED_" + gate.status);
                        install.setEnabled(true);
                        run.setEnabled(true);
                        busy.set(false);
                    });
                    return;
                }
                String message;
                try {
                    File sample = new File(new ModelLabModelStore(activity)
                            .baselineDirectory(), "test/test.wav");
                    SherpaWhisperRunner.Result receipt = SherpaWhisperRunner.decodeFixedWav(
                            store.installed(), sample);
                    message = receipt.describe();
                } catch (Throwable error) {
                    message = "M2_WHISPER_BENCH_FAILED_" + error.getClass().getSimpleName()
                            + "_" + String.valueOf(error.getMessage());
                }
                final String finishedMessage = message;
                activity.runOnUiThread(() -> {
                    status.setText("M2_WHISPER_BENCH_COMPLETED_OR_REJECTED");
                    result.setText(finishedMessage);
                    install.setEnabled(true);
                    run.setEnabled(true);
                    busy.set(false);
                });
            }, "s26-m2-sherpa-benchmark").start();
        });
        parent.addView(result);
    }
}
