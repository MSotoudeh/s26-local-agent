package io.projectir.mobile;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Push-to-talk, Persian-only, Vosk offline recognizer.
 * No model download, no network recognition, no wake-word/background mic.
 * Final recognized text requires user review before any plan execution.
 */
public final class LocalPersianAsr {
    public interface Listener {
        void onStatus(String status);
        void onTranscript(String text);
    }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Model model;
    private SpeechService speech;
    private boolean loading;
    private boolean listening;
    private int generation;

    public LocalPersianAsr(Context context) {
        this.context = context.getApplicationContext();
    }

    public File modelDirectory() {
        File root = context.getExternalFilesDir("models");
        return root == null ? null : new File(root, "vosk-model-small-fa-0.42");
    }

    public boolean modelInstalled() {
        File path = modelDirectory();
        return path != null && new File(path, "am/final.mdl").isFile()
                && new File(path, "conf/model.conf").isFile();
    }

    public synchronized boolean isActive() { return loading || listening; }

    public synchronized void begin(Listener listener) {
        if (loading || listening) {
            listener.onStatus("ASR_BUSY");
            return;
        }
        if (!modelInstalled()) {
            listener.onStatus("ASR_MODEL_NOT_INSTALLED");
            return;
        }
        loading = true;
        final int ticket = ++generation;
        listener.onStatus("ASR_LOADING_OFFLINE_MODEL");
        executor.execute(() -> {
            try {
                if (model == null) model = new Model(modelDirectory().getAbsolutePath());
                main.post(() -> startMicrophone(ticket, listener));
            } catch (Exception e) {
                main.post(() -> {
                    synchronized (LocalPersianAsr.this) {
                        if (generation == ticket) loading = false;
                    }
                    listener.onStatus("ASR_MODEL_ERROR_" + e.getClass().getSimpleName());
                });
            }
        });
    }

    private synchronized void startMicrophone(int ticket, Listener listener) {
        if (ticket != generation) return;
        loading = false;
        try {
            Recognizer recognizer = new Recognizer(model, 16000.0f);
            speech = new SpeechService(recognizer, 16000.0f);
            listening = true;
            speech.startListening(new RecognitionListener() {
                @Override public void onPartialResult(String json) {
                    // Do not run partially-recognized text as an action.
                }
                @Override public void onResult(String json) {
                    String text = PersianTranscript.parse(json);
                    if (!text.isEmpty()) listener.onTranscript(text);
                }
                @Override public void onFinalResult(String json) {
                    String text = PersianTranscript.parse(json);
                    if (!text.isEmpty()) listener.onTranscript(text);
                    synchronized (LocalPersianAsr.this) { listening = false; speech = null; }
                    listener.onStatus("ASR_STOPPED");
                }
                @Override public void onError(Exception e) {
                    synchronized (LocalPersianAsr.this) { listening = false; speech = null; }
                    listener.onStatus("ASR_MIC_ERROR");
                }
                @Override public void onTimeout() {
                    synchronized (LocalPersianAsr.this) { listening = false; speech = null; }
                    listener.onStatus("ASR_TIMEOUT");
                }
            });
            listener.onStatus("ASR_LISTENING_FA_IR_OFFLINE");
        } catch (IOException | RuntimeException e) {
            listening = false;
            speech = null;
            listener.onStatus("ASR_START_FAILED_" + e.getClass().getSimpleName());
        }
    }

    /** Deterministic offline WAV smoke test; NO microphone is activated. */
    public synchronized boolean evaluateBundledSample(Listener listener) {
        if (loading || listening || !modelInstalled()) return false;
        File sample = new File(modelDirectory(), "test/test.wav");
        if (!sample.isFile()) return false;
        loading = true;
        final int ticket = ++generation;
        listener.onStatus("ASR_EVALUATING_BUNDLED_WAV_NO_MIC");
        executor.execute(() -> {
            String result = "";
            try {
                if (model == null) model = new Model(modelDirectory().getAbsolutePath());
                // Vosk's upstream sample is 48 kHz signed 16-bit mono PCM WAV.
                try (FileInputStream stream = new FileInputStream(sample);
                     Recognizer recognizer = new Recognizer(model, 48000.0f)) {
                    byte[] header = new byte[44];
                    if (stream.read(header) != 44
                            || header[0] != 'R' || header[1] != 'I'
                            || header[2] != 'F' || header[3] != 'F') {
                        throw new IOException("WAV_HEADER_INVALID");
                    }
                    byte[] buffer = new byte[8192];
                    int size;
                    while ((size = stream.read(buffer)) > 0) {
                        recognizer.acceptWaveForm(buffer, size);
                    }
                    result = PersianTranscript.parse(recognizer.getFinalResult());
                }
            } catch (Exception e) {
                final String error = e.getClass().getSimpleName();
                main.post(() -> listener.onStatus("ASR_SAMPLE_ERROR_" + error));
            }
            final String transcript = result;
            main.post(() -> {
                synchronized (LocalPersianAsr.this) {
                    if (generation != ticket) return;
                    loading = false;
                }
                if (!transcript.isEmpty()) {
                    listener.onTranscript(transcript);
                    listener.onStatus("ASR_BUNDLED_SAMPLE_OK_OFFLINE");
                } else {
                    listener.onStatus("ASR_BUNDLED_SAMPLE_EMPTY_OR_ERROR");
                }
            });
        });
        return true;
    }

    public synchronized void stop() {
        ++generation;
        loading = false;
        if (speech != null) {
            speech.stop();
            speech = null;
        }
        listening = false;
    }

    public synchronized void release() {
        stop();
        executor.execute(() -> {
            if (model != null) { model.close(); model = null; }
        });
        executor.shutdown();
    }
}
