package io.projectir.mobile;

import org.json.JSONObject;

/** Rejects empty/oversized Vosk results. No automatic task execution. */
public final class PersianTranscript {
    private PersianTranscript() {}
    public static String parse(String hypothesis) {
        if (hypothesis == null || hypothesis.length() > 16384) return "";
        try {
            String value = new JSONObject(hypothesis).optString("text", "").trim();
            if (value.length() > 512) return "";
            return value;
        } catch (Exception ignored) {
            return "";
        }
    }
}
