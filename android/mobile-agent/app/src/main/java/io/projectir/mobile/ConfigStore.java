package io.projectir.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class ConfigStore {
    private static final String PREFS = "project_ir_agent";
    private static final String KEY_ALIAS = "project_ir_mobile_agent_token_v1";
    private static final String PREF_HUB = "hub_url";
    private static final String PREF_DEVICE = "device_id";
    private static final String PREF_TOKEN = "token_enc";
    // "<enrollment code> <new token>", kept until the Hub confirms the token.
    private static final String PREF_PENDING = "pending_enrollment_enc";
    private static final String PREF_AUTOSTART = "autostart";

    private final Context context;
    private final SharedPreferences prefs;

    public ConfigStore(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Never throws: an unreadable Keystore value (restored data, reset key)
     * means "not configured", not a crash loop in the sticky service.
     */
    public Config load() {
        String[] pending = decryptOrEmpty(prefs.getString(PREF_PENDING, null)).split(" ", 2);
        boolean hasPending = pending.length == 2
                && HubEnrollment.deviceOf(pending[0]) != null
                && HubEnrollment.validToken(pending[1]);
        return new Config(
                prefs.getString(PREF_HUB, "https://project-ir-hub.mmsotude.workers.dev"),
                prefs.getString(PREF_DEVICE, "s26"),
                decryptOrEmpty(prefs.getString(PREF_TOKEN, null)),
                prefs.getBoolean(PREF_AUTOSTART, true),
                hasPending ? pending[0] : "",
                hasPending ? pending[1] : ""
        );
    }

    /** The Hub accepted the pending token: make it the device credential. */
    public boolean promotePending(String token) {
        // A newer enrollment may have been saved while this one was in flight.
        if (!token.equals(load().pendingToken)) return false;
        return prefs.edit()
                .putString(PREF_TOKEN, encryptToken(token))
                .remove(PREF_PENDING)
                .commit();
    }

    /** The Hub refused the enrollment for good. Any earlier token is kept. */
    public void discardPending() {
        prefs.edit().remove(PREF_PENDING).commit();
    }

    /** credentialOrBlank: a one-time enrollment code (pir1....), a device token, or blank to keep. */
    public void save(String hubUrl, String deviceId, String credentialOrBlank, boolean autostart) {
        String device = normalizeDevice(deviceId);
        SharedPreferences.Editor editor = prefs.edit()
                .putString(PREF_HUB, normalizeHub(hubUrl))
                .putBoolean(PREF_AUTOSTART, autostart);
        if (HubEnrollment.looksLikeCode(credentialOrBlank)) {
            String code = credentialOrBlank.trim();
            String bound = HubEnrollment.deviceOf(code);
            if (bound == null) throw new IllegalArgumentException("invalid enrollment code");
            if (!bound.equals(device)) {
                throw new IllegalArgumentException("enrollment code is for device " + bound);
            }
            editor.putString(PREF_PENDING,
                    encryptToken(code + " " + HubEnrollment.newToken(new SecureRandom())));
        } else if (credentialOrBlank != null && !credentialOrBlank.isBlank()) {
            editor.putString(PREF_TOKEN, encryptToken(credentialOrBlank.trim()));
            editor.remove(PREF_PENDING);
        }
        editor.putString(PREF_DEVICE, device).apply();
    }

    public boolean importBootstrapIfPresent() {
        File dir = context.getExternalFilesDir(null);
        if (dir == null) return false;
        File bootstrap = new File(dir, "bootstrap.json");
        if (!bootstrap.isFile()) return false;
        try {
            String raw = new String(Files.readAllBytes(bootstrap.toPath()), StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(raw);
            String hub = json.optString("hub_url", "");
            String device = json.optString("device_id", "s26");
            String code = json.optString("enrollment_code", "");
            boolean autostart = json.optBoolean("autostart", true);
            if (hub.isBlank() || !HubEnrollment.looksLikeCode(code)) {
                throw new IllegalArgumentException("bootstrap requires hub_url and enrollment_code");
            }
            save(hub, device, code, autostart);
            return true;
        } catch (Exception e) {
            throw new IllegalStateException("bootstrap import failed", e);
        } finally {
            bootstrap.delete();
        }
    }

    public boolean hasStoredToken() {
        String enc = prefs.getString(PREF_TOKEN, null);
        return enc != null && !enc.isBlank();
    }

    private static String normalizeHub(String value) {
        if (value == null) throw new IllegalArgumentException("hub URL is required");
        String result = value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        if (!result.startsWith("https://")) {
            throw new IllegalArgumentException("hub URL must use https");
        }
        return result;
    }

    private static String normalizeDevice(String value) {
        if (value == null || value.trim().isEmpty()) return "s26";
        String result = value.trim();
        if (!HubEnrollment.validDeviceId(result)) {
            throw new IllegalArgumentException("invalid device id");
        }
        return result;
    }

    private String encryptToken(String token) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
                    + "."
                    + Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new IllegalStateException("cannot encrypt agent token", e);
        }
    }

    private String decryptOrEmpty(String envelope) {
        try {
            return decryptToken(envelope);
        } catch (IllegalStateException unreadable) {
            Log.w("ProjectIRConfig", "stored credential is unreadable; enroll the device again");
            return "";
        }
    }

    private String decryptToken(String envelope) {
        if (envelope == null || envelope.isBlank()) return "";
        try {
            String[] parts = envelope.split("\\.", 2);
            if (parts.length != 2) throw new IllegalArgumentException("invalid token envelope");
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("cannot decrypt agent token", e);
        }
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        java.security.Key existing = keyStore.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
        );
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }

    public static final class Config {
        public final String hubUrl;
        public final String deviceId;
        public final String token;
        public final boolean autostart;
        /** One-time code and the new token it will register; both empty when nothing is pending. */
        public final String pendingCode;
        public final String pendingToken;

        Config(String hubUrl, String deviceId, String token, boolean autostart,
               String pendingCode, String pendingToken) {
            this.hubUrl = hubUrl;
            this.deviceId = deviceId;
            this.token = token;
            this.autostart = autostart;
            this.pendingCode = pendingCode;
            this.pendingToken = pendingToken;
        }

        public boolean ready() {
            return hubUrl != null && hubUrl.startsWith("https://")
                    && deviceId != null && !deviceId.isBlank()
                    && (hasToken() || hasPendingEnrollment());
        }

        public boolean hasToken() {
            return token != null && !token.isBlank();
        }

        public boolean hasPendingEnrollment() {
            return !pendingCode.isBlank() && !pendingToken.isBlank();
        }
    }
}
