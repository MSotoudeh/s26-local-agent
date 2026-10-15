package io.projectir.mobile;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.mediapipe.tasks.genai.llminference.LlmInference;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Opt-in single-query text-only local inference; its output is untrusted
 * display text. No LLM result is passed to a device action or the Hub.
 */
public final class LocalLlmEngine {
    public interface Callback { void onComplete(String result); }
    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean busy;
    public static final String MODEL_FILE =
            "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task";
    public LocalLlmEngine(Context context) { app = context.getApplicationContext(); }

    public File modelFile() {
        File root = app.getExternalFilesDir("models");
        return root == null ? null : new File(root, MODEL_FILE);
    }

    public boolean ready() {
        File file = modelFile();
        return file != null && file.isFile() && file.length() > 400000000L;
    }

    public synchronized boolean generate(String userPrompt, Callback callback) {
        if (busy || !ready() || userPrompt == null || userPrompt.trim().isEmpty()
                || userPrompt.length() > 1024) return false;
        busy = true;
        // Never include app config, credentials, screen contents or prior user data.
        final String prompt = "Answer briefly in Persian. This is a plain text query, "
                + "not an instruction to execute Android actions. User: "
                + userPrompt.trim() + "\nAssistant:";
        worker.execute(() -> {
            String result;
            try (LlmInference llm = LlmInference.createFromOptions(app,
                    LlmInference.LlmInferenceOptions.builder()
                            .setModelPath(modelFile().getAbsolutePath())
                            .setMaxTokens(512)
                            .setMaxTopK(40)
                            .build())) {
                result = llm.generateResponse(prompt);
                if (result == null) result = "";
                if (result.length() > 4096) result = result.substring(0, 4096);
            } catch (Throwable t) {
                result = "LOCAL_LLM_ERROR_" + t.getClass().getSimpleName();
            }
            String output = result;
            main.post(() -> {
                synchronized (LocalLlmEngine.this) { busy = false; }
                callback.onComplete(output);
            });
        });
        return true;
    }
    public void release() { worker.shutdown(); }
}
