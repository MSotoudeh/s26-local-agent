package io.s26.pstnlab;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import org.json.JSONObject;
import java.util.Locale;
import java.util.Set;

/**
 * Non-speaking inventory of local Android speech capabilities for Persian.
 * Does not record microphone, synthesize, play sound, or install language packs.
 * An installed voice alone does not establish quality or offline inference.
 */
public final class PersianSpeechInventory {
    public interface Callback { void finished(JSONObject summary); }
    private PersianSpeechInventory() {}
    public static void inspect(Context app, Callback callback) {
        JSONObject j = new JSONObject();
        try {
            j.put("scope", "READ_ONLY_NO_MICROPHONE_NO_SYNTHESIS");
            j.put("target_language", "fa-IR");
            j.put("persian_asr_verified", false);
            j.put("persian_tts_verified", false);
            j.put("on_device_recognition_service", SpeechRecognizer.isOnDeviceRecognitionAvailable(app));
            j.put("any_recognition_service", SpeechRecognizer.isRecognitionAvailable(app));
        } catch (Throwable t) {
            try { j.put("speech_recognizer_query_error", t.getClass().getSimpleName()); }
            catch (Exception ignored) {}
        }
        final TextToSpeech[] holder = new TextToSpeech[1];
        try {
            holder[0] = new TextToSpeech(app, status ->
                new Handler(Looper.getMainLooper()).post(() -> {
                    try {
                        j.put("tts_init_code", status);
                        int persian = 0, offline = 0;
                        int langCode = -1000;
                        if (status == TextToSpeech.SUCCESS && holder[0] != null) {
                            langCode = holder[0].isLanguageAvailable(Locale.forLanguageTag("fa-IR"));
                            Set<Voice> voices = holder[0].getVoices();
                            if (voices != null) for (Voice voice : voices) {
                                if ("fa".equals(voice.getLocale().getLanguage())) {
                                    persian++;
                                    if (!voice.isNetworkConnectionRequired()) offline++;
                                }
                            }
                        }
                        j.put("fa_ir_language_available_code", langCode);
                        j.put("persian_voice_count", persian);
                        j.put("offline_persian_voice_candidates", offline);
                        j.put("no_speech_played", true);
                        j.put("actual_persian_tts_output_tested", false);
                    } catch (Throwable t) {
                        try { j.put("tts_query_error", t.getClass().getSimpleName()); }
                        catch (Exception ignored) {}
                    } finally {
                        if (holder[0] != null) holder[0].shutdown();
                        callback.finished(j);
                    }
                }));
        } catch (Throwable t) {
            try { j.put("tts_init_error", t.getClass().getSimpleName()); }
            catch (Exception ignored) {}
            callback.finished(j);
        }
    }
}
