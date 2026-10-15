package io.projectir.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;

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
    private static final String PREF_AUTOSTART = "autostart";

    private final Context context;
    private final SharedPreferences prefs;

    public ConfigStore(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public Config load() {
        return new Config(
                prefs.getString(PREF_HUB, "https://project-ir-hub.mmsotude.workers.dev"),
                prefs.getString(PREF_DEVICE, "s26"),
                decryptToken(prefs.getString(PREF_TOKEN, null)),
                prefs.getBoolean(PREF_AUTOSTART, true)
        );
    }

    public void save(String hubUrl, String deviceId, String tokenOrBlank, boolean autostart) {
        SharedPreferences.Editor editor = prefs.edit()
                .putString(PREF_HUB, normalizeHub(hubUrl))
                .putString(PREF_DEVICE, normalizeDevice(deviceId))
                .putBoolean(PREF_AUTOSTART, autostart);
        if (tokenOrBlank != null && !tokenOrBlank.isBlank()) {
            editor.putString(PREF_TOKEN, encryptToken(tokenOrBlank.trim()));
        }
        editor.apply();
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
            String token = json.optString("token", "");
            boolean autostart = json.optBoolean("autostart", true);
            if (hub.isBlank() || token.isBlank()) {
                throw new IllegalArgumentException("bootstrap requires hub_url and token");
            }
            save(hub, device, token, autostart);
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
        if (!result.matches("[A-Za-z0-9._-]{1,64}")) {
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

        Config(String hubUrl, String deviceId, String token, boolean autostart) {
            this.hubUrl = hubUrl;
            this.deviceId = deviceId;
            this.token = token;
            this.autostart = autostart;
        }

        public boolean ready() {
            return hubUrl != null && hubUrl.startsWith("https://")
                    && deviceId != null && !deviceId.isBlank()
                    && token != null && !token.isBlank();
        }
    }
}
