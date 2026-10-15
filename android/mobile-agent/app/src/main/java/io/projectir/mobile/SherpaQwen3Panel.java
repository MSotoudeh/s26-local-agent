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
public final class SherpaQwen3Panel {
    private SherpaQwen3Panel() {}

    public static void attach(Activity activity, LinearLayout parent, LocalPersianAsr asr) {
        SherpaQwen3Store store = new SherpaQwen3Store(activity);
        TextView title = new TextView(activity);
        title.setText("M2 sherpa-onnx Qwen3-ASR 0.6B Int8 (independent ASR lab)");
        title.setTextSize(18f);
        parent.addView(title);
        TextView status = new TextView(activity);
        status.setText("M2_QWEN3_ASR_NOT_ACTIVE | pending SHA256-verified installation");
        parent.addView(status);
        TextView result = new TextView(activity);
        result.setText("M2_QWEN3_ASR_NO_BENCHMARK_YET");
        result.setTextIsSelectable(true);
        AtomicBoolean busy = new AtomicBoolean();

        Button install = new Button(activity);
        install.setId(R.id.m2_qwen3_install);
        install.setText("Verify & install staged Qwen3-ASR 0.6B (offline)");
        parent.addView(install);
        Button run = new Button(activity);
        run.setId(R.id.m2_qwen3_benchmark);
        run.setText("Benchmark Qwen3-ASR on SAME bundled WAV (no microphone)");
        parent.addView(run);

        install.setOnClickListener(view -> {
            if (asr.isActive() || !busy.compareAndSet(false, true)) {
                status.setText("M2_QWEN3_ASR_OPERATION_BUSY_OR_MIC_ACTIVE");
                return;
            }
            install.setEnabled(false);
            run.setEnabled(false);
            status.setText("M2_QWEN3_ASR_INSTALL_VERIFYING_FILE_SHA256");
            new Thread(() -> {
                SherpaQwen3Store.Result done = store.installVerifiedStage();
                activity.runOnUiThread(() -> {
                    busy.set(false);
                    install.setEnabled(true);
                    run.setEnabled(true);
                    status.setText("M2_QWEN3_ASR_INSTALL_" + done.state
                            + " | files=" + done.files + " bytes=" + done.bytes);
                });
            }, "s26-m2-qwen3-verify-stage").start();
        });

        run.setOnClickListener(view -> {
            if (asr.isActive() || !busy.compareAndSet(false, true)) {
                status.setText("M2_QWEN3_ASR_BENCH_BUSY_OR_MIC_ACTIVE");
                return;
            }
            install.setEnabled(false);
            run.setEnabled(false);
            status.setText("M2_QWEN3_ASR_PRECHECK_RUNNING");
            new Thread(() -> {
                SherpaQwen3Store.Result gate = store.heavyPreflight();
                if (!gate.ok) {
                    activity.runOnUiThread(() -> {
                        status.setText("M2_QWEN3_ASR_BENCH_GATED_" + gate.state);
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
                    SherpaQwen3Runner.Result receipt = SherpaQwen3Runner.decodeFixedWav(
                            store.installed(), sample);
                    message = receipt.describe();
                } catch (Throwable error) {
                    message = "M2_QWEN3_ASR_BENCH_FAILED_" + error.getClass().getSimpleName()
                            + "_" + String.valueOf(error.getMessage());
                }
                final String finishedMessage = message;
                activity.runOnUiThread(() -> {
                    status.setText("M2_QWEN3_ASR_BENCH_COMPLETED_OR_REJECTED");
                    result.setText(finishedMessage);
                    install.setEnabled(true);
                    run.setEnabled(true);
                    busy.set(false);
                });
            }, "s26-m2-qwen3-asr-benchmark").start();
        });
        parent.addView(result);
    }
}
