package io.projectir.mobile;

import android.app.ActivityManager;
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

/**
 * Qwen3-ASR public release: signed-APK-pinned SHA256 model store.
 * No arbitrary download path, no network calls, no delete, no file overwrite.
 * App-scoped USB staging -> verify full file inventory -> atomic rename.
 */
public final class SherpaQwen3Store {
    public static final String MODEL_ID = "asr.qwen3.0_6b.int8";
    public static final String DIRECTORY = "sherpa-qwen3-asr-0.6b-int8";
    public static final String STAGING = ".staging-sherpa-qwen3-asr-0.6b-int8";
    private static final String MANIFEST = "s26_qwen3_asr_manifest.json";
    private static final long RESERVE_BYTES = 30_000_000_000L;
    private static final long MIN_AVAILABLE_MEM = 3_000_000_000L;
    private final Context context;

    public SherpaQwen3Store(Context context) {
        this.context = context.getApplicationContext();
    }

    public static final class Result {
        public final boolean ok;
        public final String state;
        public final int files;
        public final long bytes;
        Result(boolean ok, String state, int files, long bytes) {
            this.ok = ok; this.state = state; this.files = files; this.bytes = bytes;
        }
    }

    private static Result fail(String status) { return new Result(false, status, 0, 0); }

    private File root() {
        File base = context.getExternalFilesDir("models-lab");
        return base == null ? null : new File(base, "asr");
    }
    public File installed() {
        File base = root();
        return base == null ? null : new File(base, DIRECTORY);
    }
    public File staging() {
        File base = root();
        return base == null ? null : new File(base, STAGING);
    }

    public synchronized Result installVerifiedStage() {
        File base = root(), dest = installed(), stage = staging();
        if (base == null || dest == null || stage == null) return fail("STORAGE_UNAVAILABLE");
        if (dest.exists()) {
            Result prior = verifyAt(dest);
            return prior.ok ? new Result(true, "ALREADY_VERIFIED", prior.files, prior.bytes)
                    : fail("INSTALLED_DIRECTORY_COLLISION");
        }
        if (!base.isDirectory() && !base.mkdirs()) return fail("CREATE_STORAGE_DIR_FAILED");
        if (base.getUsableSpace() < RESERVE_BYTES) return fail("STORAGE_RESERVE");
        Result verified = verifyAt(stage);
        if (!verified.ok) return verified;
        if (!stage.renameTo(dest)) return fail("ATOMIC_RENAME_FAILED");
        return new Result(true, "SHA256_VERIFIED_INSTALLED", verified.files, verified.bytes);
    }

    public Result verifyInstalled() { return verifyAt(installed()); }

    private Result verifyAt(File directory) {
        if (directory == null || !directory.isDirectory()) return fail("MODEL_DIR_NOT_FOUND");
        try {
            JSONObject manifest = readManifest();
            if (!"1.0.0".equals(manifest.getString("schema_version"))
                    || !MODEL_ID.equals(manifest.getString("model_id"))
                    || !DIRECTORY.equals(manifest.getString("directory"))
                    || !"apache-2.0".equals(manifest.getString("license"))) {
                return fail("UNSUPPORTED_SIGNED_MANIFEST");
            }
            JSONArray records = manifest.getJSONArray("files");
            if (records.length() < 5 || records.length() > 100) {
                return fail("MANIFEST_FILE_COUNT");
            }
            String rootPath = directory.getCanonicalPath() + File.separator;
            Set<String> unique = new HashSet<>();
            long total = 0;
            for (int i = 0; i < records.length(); i++) {
                JSONObject item = records.getJSONObject(i);
                String rel = item.getString("path");
                if (!allowedRelativePath(rel) || !unique.add(rel)) {
                    return fail("INVALID_MANIFEST_PATH_OR_DUPLICATE");
                }
                File file = new File(directory, rel);
                if (!file.getCanonicalPath().startsWith(rootPath)
                        || java.nio.file.Files.isSymbolicLink(file.toPath())) {
                    return fail("SYMLINK_OR_PATH_ESCAPE");
                }
                long requiredBytes = item.getLong("bytes");
                if (requiredBytes < 1 || requiredBytes > 2_000_000_000L
                        || !file.isFile() || file.length() != requiredBytes
                        || !hash(file).equalsIgnoreCase(item.getString("sha256"))) {
                    return fail("FILE_SHA_OR_LENGTH_MISMATCH");
                }
                total += requiredBytes;
            }
            if (total != manifest.getLong("total_expanded_bytes")
                    || countRecursive(directory) != records.length()) {
                return fail("FILE_INVENTORY_MISMATCH");
            }
            return new Result(true, "ALL_FILE_HASHES_VERIFIED", records.length(), total);
        } catch (Exception e) {
            return fail("VERIFY_ERROR_" + e.getClass().getSimpleName());
        }
    }

    private static boolean allowedRelativePath(String name) {
        if (name == null || name.length() > 190 || name.contains("..")
                || name.startsWith("/") || name.contains("\\")
                || name.contains(":") || name.contains("//")) return false;
        if (name.equals("conv_frontend.onnx") || name.equals("encoder.int8.onnx")
                || name.equals("decoder.int8.onnx") || name.equals("tokens.txt")) return true;
        if (!name.startsWith("tokenizer/")) return false;
        return name.matches("[a-zA-Z0-9_./-]+") && name.split("/").length >= 2;
    }

    private static int countRecursive(File node) throws Exception {
        if (java.nio.file.Files.isSymbolicLink(node.toPath()))
            throw new SecurityException("SYMLINK_NOT_ALLOWED");
        if (node.isFile()) return 1;
        if (!node.isDirectory()) throw new SecurityException("INVALID_MODEL_NODE");
        File[] children = node.listFiles();
        if (children == null) throw new SecurityException("UNREADABLE_MODEL_NODE");
        int total = 0;
        for (File child : children) {
            total += countRecursive(child);
            if (total > 100) throw new SecurityException("MODEL_FILE_COUNT_CAP");
        }
        return total;
    }

    private JSONObject readManifest() throws Exception {
        try (InputStream input = context.getAssets().open(MANIFEST)) {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] block = new byte[4096];
            int len;
            while ((len = input.read(block)) > 0) {
                if (buffer.size() + len > 32768) throw new IllegalArgumentException("MANIFEST_TOO_BIG");
                buffer.write(block, 0, len);
            }
            return new JSONObject(new String(buffer.toByteArray(),
                    java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    public Result heavyPreflight() {
        Result verified = verifyInstalled();
        if (!verified.ok) return verified;
        File base = root();
        if (base == null || base.getUsableSpace() < RESERVE_BYTES) return fail("STORAGE_RESERVE");
        ActivityManager am=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);
        if (info.lowMemory || info.availMem < MIN_AVAILABLE_MEM) return fail("RAM_GUARD");
        PowerManager pm=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
        if (pm != null && pm.getCurrentThermalStatus() >= PowerManager.THERMAL_STATUS_SEVERE)
            return fail("THERMAL_GUARD");
        BatteryManager bm=(BatteryManager)context.getSystemService(Context.BATTERY_SERVICE);
        if (bm != null) {
            int percent=bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            if (percent >= 0 && percent < 30) return fail("BATTERY_GUARD");
        }
        return new Result(true,"READY_TO_RUN",verified.files,verified.bytes);
    }

    private static String hash(File file) throws Exception {
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        try (FileInputStream input=new FileInputStream(file)) {
            byte[] buf=new byte[1048576];int n;
            while ((n=input.read(buf)) != -1) if(n>0) md.update(buf,0,n);
        }
        StringBuilder b=new StringBuilder();
        for(byte value:md.digest()) {
            b.append(Character.forDigit((value>>>4)&15,16));
            b.append(Character.forDigit(value&15,16));
        }
        return b.toString();
    }
}
