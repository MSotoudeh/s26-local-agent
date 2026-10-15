package io.projectir.mobile;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * M1b local ModelStore. A pinned manifest is shipped in the signed APK.
 * ADB/USB may stage public files outside the active directory; no HTTP,
 * shell, overwrite or arbitrary path is accepted by this app-side store.
 *
 * Verification checks every file's SHA-256 and size, rejecting symlinks
 * escaping the canonical root. Same-filesystem rename commits after
 * verification. Failed stages are retained for diagnosis, not executed.
 */
public final class ModelLabModelStore {
    public static final String BASELINE_ID = "asr.vosk.fa-small-042";
    public static final String CANDIDATE_ID = "asr.vosk.fa-small-05";
    private static final String BASELINE_DIR = "vosk-model-small-fa-0.42";
    private static final String CANDIDATE_DIR = "vosk-model-small-fa-0.5";
    private static final String STAGING_DIR = ".staging-vosk-model-small-fa-0.5";
    private static final String MANIFEST = "vosk_fa_small_05_manifest.json";
    private static final long MIN_FREE_BYTES = 30_000_000_000L;
    private final Context context;

    public static final class Receipt {
        public final boolean ok;
        public final String state;
        public final int files;
        public final long bytes;
        Receipt(boolean ok, String state, int files, long bytes) {
            this.ok = ok; this.state = state; this.files = files; this.bytes = bytes;
        }
    }

    public ModelLabModelStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public File baselineDirectory() {
        File root = context.getExternalFilesDir("models");
        return root == null ? null : new File(root, BASELINE_DIR);
    }

    private File taskRoot() {
        File root = context.getExternalFilesDir("models-lab");
        return root == null ? null : new File(root, "asr");
    }

    public File candidateDirectory() {
        File root = taskRoot();
        return root == null ? null : new File(root, CANDIDATE_DIR);
    }

    public File stagingDirectory() {
        File root = taskRoot();
        return root == null ? null : new File(root, STAGING_DIR);
    }

    public File resolve(String id) {
        if (BASELINE_ID.equals(id)) return baselineDirectory();
        if (CANDIDATE_ID.equals(id)) return candidateDirectory();
        return null;
    }

    public boolean baselineAvailable() {
        File root = baselineDirectory();
        return root != null && new File(root, "am/final.mdl").isFile()
                && new File(root, "conf/model.conf").isFile();
    }

    public boolean candidatePresent() {
        File root = candidateDirectory();
        return root != null && new File(root, "am/final.mdl").isFile()
                && new File(root, "conf/model.conf").isFile();
    }

    public Receipt verifyCandidate() { return verifyAt(candidateDirectory()); }
    public Receipt verifyStaging() { return verifyAt(stagingDirectory()); }

    public synchronized Receipt installStagedCandidate() {
        File staging = stagingDirectory();
        File installed = candidateDirectory();
        File task = taskRoot();
        if (staging == null || installed == null || task == null) {
            return new Receipt(false, "NO_EXTERNAL_STORAGE", 0, 0);
        }
        if (installed.exists()) {
            Receipt existing = verifyAt(installed);
            return existing.ok ? new Receipt(true, "ALREADY_VERIFIED", existing.files,
                    existing.bytes) : new Receipt(false, "FINAL_COLLISION", 0, 0);
        }
        if (!task.isDirectory() && !task.mkdirs()) {
            return new Receipt(false, "NO_TASK_DIRECTORY", 0, 0);
        }
        if (task.getUsableSpace() < MIN_FREE_BYTES) {
            return new Receipt(false, "FREE_STORAGE_BELOW_RESERVE", 0, 0);
        }
        Receipt receipt = verifyAt(staging);
        if (!receipt.ok) return receipt;
        // renameTo is a same-volume atomic directory move. Never replace.
        if (!staging.renameTo(installed)) {
            return new Receipt(false, "ATOMIC_RENAME_FAILED", receipt.files, receipt.bytes);
        }
        return new Receipt(true, "INSTALLED_SHA256_VERIFIED", receipt.files, receipt.bytes);
    }

    private Receipt verifyAt(File root) {
        if (root == null || !root.isDirectory()) {
            return new Receipt(false, "MODEL_DIRECTORY_ABSENT", 0, 0);
        }
        try {
            JSONObject manifest = manifest();
            if (!CANDIDATE_ID.equals(manifest.getString("model_id"))
                    || !CANDIDATE_DIR.equals(manifest.getString("directory"))
                    || !"apache-2.0".equals(manifest.getString("license"))) {
                return new Receipt(false, "APK_MANIFEST_INVALID", 0, 0);
            }
            String base = root.getCanonicalPath() + File.separator;
            JSONArray entries = manifest.getJSONArray("files");
            long total = 0;
            int count = 0;
            for (int i = 0; i < entries.length(); i++) {
                JSONObject record = entries.getJSONObject(i);
                String rel = record.getString("path");
                if (!safeRelative(rel)) return new Receipt(false, "UNSAFE_MODEL_PATH", count, total);
                File file = new File(root, rel);
                if (!file.getCanonicalPath().startsWith(base)) {
                    return new Receipt(false, "MODEL_PATH_ESCAPE", count, total);
                }
                long length = record.getLong("bytes");
                if (!file.isFile() || file.length() != length) {
                    return new Receipt(false, "MODEL_FILE_SIZE_MISMATCH", count, total);
                }
                if (!digest(file).equalsIgnoreCase(record.getString("sha256"))) {
                    return new Receipt(false, "MODEL_FILE_SHA256_MISMATCH", count, total);
                }
                total += length; count++;
            }
            if (entries.length() == 0 || total != manifest.getLong("total_expanded_bytes")
                    || countFiles(root) != entries.length()) {
                return new Receipt(false, "MANIFEST_FILE_SET_MISMATCH", count, total);
            }
            return new Receipt(true, "SHA256_VERIFIED", count, total);
        } catch (Exception ex) {
            return new Receipt(false, "VERIFY_FAILED_" + ex.getClass().getSimpleName(), 0, 0);
        }
    }

    private JSONObject manifest() throws Exception {
        try (InputStream in = context.getAssets().open(MANIFEST)) {
            byte[] content = new byte[64 * 1024];
            int index = 0;
            int read;
            while ((read = in.read(content, index, content.length - index)) != -1) {
                if (read == 0 || (index += read) == content.length) {
                    throw new IllegalStateException("MANIFEST_TOO_LARGE");
                }
            }
            return new JSONObject(new String(content, 0, index,
                    java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static boolean safeRelative(String s) {
        if (s == null || s.isEmpty() || s.startsWith("/") || s.contains("\\")
                || s.contains(":") || s.contains("//") || s.contains("..")) return false;
        for (String part : s.split("/")) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) return false;
        }
        return true;
    }

    private static int countFiles(File root) throws Exception {
        if (java.nio.file.Files.isSymbolicLink(root.toPath())) {
            throw new SecurityException("MODEL_SYMLINK_NOT_ALLOWED");
        }
        if (root.isFile()) return 1;
        if (!root.isDirectory()) throw new SecurityException("INVALID_MODEL_ENTRY");
        File[] children = root.listFiles();
        if (children == null) throw new SecurityException("UNREADABLE_MODEL_TREE");
        int total = 0;
        for (File child : children) {
            total += countFiles(child);
            if (total > 1500) throw new SecurityException("MODEL_FILE_COUNT_OVER_CAP");
        }
        return total;
    }

    private static String digest(File file) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] block = new byte[2 * 1024 * 1024];
            int n;
            while ((n = in.read(block)) > 0) sha.update(block, 0, n);
        }
        StringBuilder out = new StringBuilder(64);
        for (byte b : sha.digest()) {
            out.append(Character.forDigit((b >>> 4) & 15, 16))
                    .append(Character.forDigit(b & 15, 16));
        }
        return out.toString();
    }
}
