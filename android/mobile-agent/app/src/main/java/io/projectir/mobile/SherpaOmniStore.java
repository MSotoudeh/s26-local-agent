package io.projectir.mobile;

import android.content.Context;
import android.os.BatteryManager;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

/** M2 model store: signed-APK pinned per-file hash verification and atomic install.
 * Deliberately *no* HTTP and no arbitrary user-chosen path. All public weights
 * are transferred into staging over owner's USB by a separately verified tool.
 */
public final class SherpaOmniStore {
    public static final String MODEL_ID = "asr.omni_ctc_300m.int8";
    public static final String DIRECTORY = "sherpa-omni300m-ctc-int8";
    public static final String STAGING = ".staging-sherpa-omni300m-ctc-int8";
    public static final String MANIFEST = "s26_omni300m_manifest.json";
    private static final long MIN_STORAGE_FREE = 30_000_000_000L;
    private final Context context;

    public SherpaOmniStore(Context ctx) { context = ctx.getApplicationContext(); }

    private File root() {
        File path = context.getExternalFilesDir("models-lab");
        return path == null ? null : new File(path, "asr");
    }

    public File installed() {
        File path = root();
        return path == null ? null : new File(path, DIRECTORY);
    }

    public File staging() {
        File path = root();
        return path == null ? null : new File(path, STAGING);
    }

    public static final class Result {
        public final boolean ok;
        public final String status;
        public final int files;
        public final long bytes;
        Result(boolean ok, String status, int files, long bytes) {
            this.ok = ok; this.status = status; this.files = files; this.bytes = bytes;
        }
    }

    private static Result fail(String status) { return new Result(false, status, 0, 0); }

    public synchronized Result installVerifiedStage() {
        File base = root();
        File current = installed();
        File staged = staging();
        if (base == null || current == null || staged == null) {
            return fail("NO_STORAGE");
        }
        if (current.exists()) {
            Result prior = verify(current);
            return prior.ok ? new Result(true, "ALREADY_VERIFIED", prior.files, prior.bytes)
                    : fail("FINAL_DIR_COLLISION");
        }
        if (!base.isDirectory() && !base.mkdirs()) return fail("NO_ROOT_DIR");
        if (base.getUsableSpace() < MIN_STORAGE_FREE) return fail("STORAGE_GUARD");
        Result candidate = verify(staged);
        if (!candidate.ok) return candidate;
        if (!staged.renameTo(current)) return fail("ATOMIC_MOVE_FAILED");
        return new Result(true, "SHA256_VERIFIED_INSTALLED", candidate.files, candidate.bytes);
    }

    public Result verifyInstalled() { return verify(installed()); }

    private Result verify(File path) {
        if (path == null || !path.isDirectory()) return fail("DIRECTORY_MISSING");
        try {
            JSONObject m;
            try (InputStream input = context.getAssets().open(MANIFEST)) {
                byte[] bytes = new byte[16_384];
                int length = 0, n;
                while ((n = input.read(bytes, length, bytes.length - length)) != -1) {
                    if (n == 0 || (length += n) >= bytes.length) {
                        return fail("MANIFEST_TOO_BIG");
                    }
                }
                m = new JSONObject(new String(bytes, 0, length,
                        java.nio.charset.StandardCharsets.UTF_8));
            }
            if (!"1.0.0".equals(m.getString("schema_version"))
                    || !MODEL_ID.equals(m.getString("model_id"))
                    || !DIRECTORY.equals(m.getString("directory"))
                    || !"apache-2.0".equals(m.getString("license"))) {
                return fail("BAD_SIGNED_MANIFEST");
            }
            String root = path.getCanonicalPath() + File.separator;
            JSONArray files = m.getJSONArray("files");
            long total = 0;
            int count = 0;
            Set<String> visited = new HashSet<>();
            for (int i = 0; i < files.length(); i++) {
                JSONObject record = files.getJSONObject(i);
                String relative = record.getString("path");
                if (!("model.int8.onnx".equals(relative)
                        || "tokens.txt".equals(relative)
                        || "LICENSE".equals(relative)) || !visited.add(relative)) {
                    return fail("UNEXPECTED_MANIFEST_ENTRY");
                }
                File f = new File(path, relative);
                if (!f.getCanonicalPath().startsWith(root)
                        || java.nio.file.Files.isSymbolicLink(f.toPath())) {
                    return fail("ESCAPE_OR_SYMLINK");
                }
                long expected = record.getLong("bytes");
                if (!f.isFile() || f.length() != expected
                        || !digest(f).equalsIgnoreCase(record.getString("sha256"))) {
                    return fail("FILE_SHA256_OR_LENGTH_FAILURE");
                }
                count++;
                total += expected;
            }
            if (count != 3 || path.listFiles() == null || path.listFiles().length != 3
                    || total != m.getLong("total_expanded_bytes")) {
                return fail("FILE_SET_MISMATCH");
            }
            return new Result(true, "ALL_FILES_SHA256_VERIFIED", count, total);
        } catch (Exception e) {
            return fail("VERIFY_ERROR_" + e.getClass().getSimpleName());
        }
    }

    public Result heavyPreflight() {
        Result valid = verifyInstalled();
        if (!valid.ok) return valid;
        File root = root();
        if (root == null || root.getUsableSpace() < MIN_STORAGE_FREE) {
            return fail("STORAGE_GUARD");
        }
        android.app.ActivityManager mgr = (android.app.ActivityManager)
                context.getSystemService(Context.ACTIVITY_SERVICE);
        android.app.ActivityManager.MemoryInfo state =
                new android.app.ActivityManager.MemoryInfo();
        mgr.getMemoryInfo(state);
        if (state.availMem < 2_000_000_000L || state.lowMemory) {
            return fail("RAM_GUARD");
        }
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm != null && pm.getCurrentThermalStatus()
                >= PowerManager.THERMAL_STATUS_SEVERE) {
            return fail("THERMAL_GUARD");
        }
        BatteryManager battery = (BatteryManager)
                context.getSystemService(Context.BATTERY_SERVICE);
        if (battery != null) {
            int pct = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            if (pct >= 0 && pct < 30) return fail("BATTERY_GUARD");
        }
        return new Result(true, "PREFLIGHT_PASS", valid.files, valid.bytes);
    }

    private static String digest(File file) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] data = new byte[1_048_576];
            int n;
            while ((n = input.read(data)) != -1) {
                if (n > 0) hash.update(data, 0, n);
            }
        }
        StringBuilder builder = new StringBuilder(64);
        for (byte b : hash.digest()) {
            builder.append(Character.forDigit((b >> 4) & 15, 16));
            builder.append(Character.forDigit(b & 15, 16));
        }
        return builder.toString();
    }
}
