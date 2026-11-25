package io.projectir.mobile;

import android.content.Context;
import android.os.Process;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ShellUserService extends IShellService.Stub {
    private static final int MAX_STREAM_BYTES = 384 * 1024;

    /**
     * Prints its pid, then becomes a session leader running the command ($0), so
     * that a timeout can kill the whole group. Killing only the shell does not
     * work: it ignores SIGTERM while a child runs, and the rest of the command
     * would still execute after the caller was told it timed out.
     */
    private static final String LAUNCHER =
            "echo $$; exec /system/bin/setsid /system/bin/sh -c \"$0\"";

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
            ProcessBuilder builder = new ProcessBuilder("/system/bin/sh", "-c", LAUNCHER, command);
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
            // No input is ever sent: without EOF, `cat` or `read` would hang to the timeout.
            process.getOutputStream().close();
            final java.lang.Process p = process;
            int group = readPid(p.getInputStream());
            Thread outThread = new Thread(() -> stdout.drain(p.getInputStream()), "project-ir-shell-stdout");
            Thread errThread = new Thread(() -> stderr.drain(p.getErrorStream()), "project-ir-shell-stderr");
            outThread.start();
            errThread.start();

            boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!completed) {
                signalGroup(group, OsConstants.SIGTERM);
                if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                }
                // Also without a survivor check: a child may have ignored SIGTERM
                // after its shell exited.
                signalGroup(group, OsConstants.SIGKILL);
                process.waitFor(500, TimeUnit.MILLISECONDS);
            }
            outThread.join(1500);
            errThread.join(1500);

            int exitCode = completed ? process.exitValue() : -1;
            result.put("command", command);
            result.put("cwd", cwd == null || cwd.isBlank() ? JSONObject.NULL : cwd);
            result.put("shell", "sh");
            result.put("pid", group);
            result.put("uid", Process.myUid());
            result.put("exit_code", exitCode);
            result.put("timed_out", !completed);
            result.put("duration_ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            OutputBudget output = OutputBudget.fit(
                    stdout.text(), stderr.text(), OutputBudget.MAX_JSON_BYTES);
            result.put("stdout", output.stdout);
            result.put("stderr", output.stderr);
            result.put("stdout_truncated", stdout.truncated.get() || output.stdoutCut);
            result.put("stderr_truncated", stderr.truncated.get() || output.stderrCut);
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
                OutputBudget output = OutputBudget.fit(
                        stdout.text(), stderr.text(), OutputBudget.MAX_JSON_BYTES);
                result.put("stdout", output.stdout);
                result.put("stderr", output.stderr);
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

    /** First line of the launcher's output; the command has not started yet. */
    private static int readPid(InputStream stdout) throws IOException {
        int pid = 0;
        for (int c = stdout.read(); c != '\n'; c = stdout.read()) {
            if (c < '0' || c > '9' || pid > 100_000_000) throw new IOException("shell did not start");
            pid = pid * 10 + (c - '0');
        }
        // kill(0) and kill(-1) address far more than this command.
        if (pid <= 1) throw new IOException("shell did not start");
        return pid;
    }

    private static void signalGroup(int group, int signal) {
        try {
            Os.kill(-group, signal);
        } catch (ErrnoException alreadyGone) {
            // Every process of the group has exited.
        }
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
            return OutputBudget.decode(buffer.toByteArray(), truncated.get());
        }
    }
}
