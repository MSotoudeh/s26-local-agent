package io.projectir.mobile;

import android.content.Context;
import android.os.Process;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ShellUserService extends IShellService.Stub {
    private static final int MAX_STREAM_BYTES = 384 * 1024;

    public ShellUserService() {
    }

    public ShellUserService(Context context) {
    }

    @Override
    public String exec(String command, String cwd, String envJson, int timeoutSeconds) {
        long started = System.nanoTime();
        JSONObject result = new JSONObject();
        java.lang.Process process = null;
        StreamCapture stdout = new StreamCapture();
        StreamCapture stderr = new StreamCapture();
        try {
            if (command == null || command.isBlank()) {
                throw new IllegalArgumentException("command is required");
            }
            int timeout = Math.max(1, Math.min(timeoutSeconds <= 0 ? 60 : timeoutSeconds, 300));
            ProcessBuilder builder = new ProcessBuilder("/system/bin/sh", "-c", command);
            if (cwd != null && !cwd.isBlank()) {
                File dir = new File(cwd);
                if (!dir.isDirectory()) throw new IllegalArgumentException("cwd is not a directory: " + cwd);
                builder.directory(dir);
            }
            if (envJson != null && !envJson.isBlank()) {
                JSONObject env = new JSONObject(envJson);
                Iterator<String> keys = env.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object value = env.opt(key);
                    if (value != null && value != JSONObject.NULL) {
                        builder.environment().put(key, String.valueOf(value));
                    }
                }
            }

            process = builder.start();
            final java.lang.Process p = process;
            Thread outThread = new Thread(() -> stdout.drain(p.getInputStream()), "project-ir-shell-stdout");
            Thread errThread = new Thread(() -> stderr.drain(p.getErrorStream()), "project-ir-shell-stderr");
            outThread.start();
            errThread.start();

            boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!completed) {
                process.destroy();
                if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(500, TimeUnit.MILLISECONDS);
                }
            }
            outThread.join(1500);
            errThread.join(1500);

            int exitCode = completed ? process.exitValue() : -1;
            result.put("command", command);
            result.put("cwd", cwd == null || cwd.isBlank() ? JSONObject.NULL : cwd);
            result.put("shell", "sh");
            result.put("pid", safePid(process));
            result.put("uid", Process.myUid());
            result.put("exit_code", exitCode);
            result.put("timed_out", !completed);
            result.put("duration_ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            result.put("stdout", stdout.text());
            result.put("stderr", stderr.text());
            result.put("stdout_truncated", stdout.truncated.get());
            result.put("stderr_truncated", stderr.truncated.get());
            return result.toString();
        } catch (Exception e) {
            try {
                result.put("command", command == null ? JSONObject.NULL : command);
                result.put("cwd", cwd == null ? JSONObject.NULL : cwd);
                result.put("shell", "sh");
                result.put("uid", Process.myUid());
                result.put("exit_code", -1);
                result.put("timed_out", false);
                result.put("duration_ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                result.put("stdout", stdout.text());
                result.put("stderr", stderr.text());
                result.put("error_type", e.getClass().getSimpleName());
                result.put("error_message", String.valueOf(e.getMessage()));
            } catch (Exception ignored) {
            }
            return result.toString();
        } finally {
            if (process != null) {
                try {
                    process.getInputStream().close();
                    process.getErrorStream().close();
                    process.getOutputStream().close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static long safePid(java.lang.Process process) {
        // Android's java.lang.Process API does not expose Process.pid() in the
        // platform stubs used by AGP. PID is diagnostic-only, so report unknown.
        return -1;
    }

    @Override
    public void destroy() {
        System.exit(0);
    }

    private static final class StreamCapture {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final AtomicBoolean truncated = new AtomicBoolean(false);

        void drain(InputStream input) {
            byte[] chunk = new byte[8192];
            try {
                int read;
                while ((read = input.read(chunk)) != -1) {
                    int remaining = MAX_STREAM_BYTES - buffer.size();
                    if (remaining > 0) {
                        buffer.write(chunk, 0, Math.min(read, remaining));
                    }
                    if (read > remaining) truncated.set(true);
                }
            } catch (Exception ignored) {
            }
        }

        String text() {
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
