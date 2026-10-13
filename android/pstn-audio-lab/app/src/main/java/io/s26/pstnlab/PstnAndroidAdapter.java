package io.s26.pstnlab;

import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.Process;
import android.os.SystemClock;
import android.telecom.TelecomManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * EXPERIMENTAL shell-side PSTN PCM adapter for Android API 37.
 *
 * Shizuku UserService must supply an Android Context. A caller must explicitly
 * confirm a permitted live test and prove the cellular call is active.
 *
 * There is NO CLI path to activate real audio: main() is in the separate
 * PstnAdapterSelfTest and only uses a fake backend.
 *
 * This class has NOT been tested with a real S26 Ultra call. Do not integrate
 * into a production calling workflow before live capture+uplink validation.
 */
public final class PstnAndroidAdapter implements AutoCloseable {
    public static final int RATE_HZ = 16000;
    public static final int FRAME_MS = 20;
    public static final int FRAME_BYTES = 640; // 16k * 20ms * mono S16LE
    public static final int SHELL_UID = 2000;
    private static final long HEARTBEAT_LIMIT_MS = 3000;
    private static final String SHELL_PACKAGE = "com.android.shell";

    public enum State { IDLE, RUNNING, CLOSED }

    public interface Backend {
        void open() throws Exception;
        int read(byte[] target) throws Exception;
        int write(byte[] frame) throws Exception;
        void close();
    }

    public interface PhoneGate {
        boolean hasActiveCellularCall();
    }

    /** No misleading inference from the static AudioPolicy metadata. */
    public static final class Authorization {
        public final boolean ownerApproved;
        public final boolean farEndInformed;
        public final boolean uplinkApproved;
        public Authorization(boolean ownerApproved, boolean farEndInformed, boolean uplinkApproved) {
            this.ownerApproved = ownerApproved;
            this.farEndInformed = farEndInformed;
            this.uplinkApproved = uplinkApproved;
        }
    }

    private final Backend backend;
    private final PhoneGate gate;
    private final Authorization approval;
    private State state = State.IDLE;
    private long heartbeatMs = -1L;
    private long rxFrames = 0;
    private long txFrames = 0;
    private final byte[] rxPending = new byte[FRAME_BYTES];
    private int rxPendingCount = 0;

    public PstnAndroidAdapter(Backend backend, PhoneGate gate, Authorization approval) {
        if (backend == null || gate == null || approval == null) throw new IllegalArgumentException("null dependency");
        this.backend = backend;
        this.gate = gate;
        this.approval = approval;
    }

    public synchronized State getState() { return state; }
    public synchronized long getRxFrames() { return rxFrames; }
    public synchronized long getTxFrames() { return txFrames; }

    public synchronized void start() throws Exception {
        if (state != State.IDLE) throw new IllegalStateException("not idle");
        if (!approval.ownerApproved || !approval.farEndInformed) {
            throw new SecurityException("owner approval and participant notice required");
        }
        if (!approval.uplinkApproved) throw new SecurityException("uplink approval required before opening track");
        if (!gate.hasActiveCellularCall()) throw new SecurityException("no active cellular call");
        try {
            backend.open();
            // Opening and tracking audio is an explicit operation. A failure
            // cannot leave the stream running.
            state = State.RUNNING;
            heartbeatMs = SystemClock.elapsedRealtime();
            new Thread(() -> {
                while (true) {
                    SystemClock.sleep(200);
                    synchronized (PstnAndroidAdapter.this) {
                        if (state != State.RUNNING) return;
                        boolean live;
                        try { live = gate.hasActiveCellularCall(); }
                        catch (RuntimeException e) { live = false; }
                        if (!live || SystemClock.elapsedRealtime() - heartbeatMs > HEARTBEAT_LIMIT_MS) {
                            close();
                            return;
                        }
                    }
                }
            }, "pstn-audio-watchdog").start();
        } catch (Exception | Error e) {
            close();
            throw e;
        }
    }

    public synchronized void heartbeat() {
        enforceLive();
        heartbeatMs = SystemClock.elapsedRealtime();
    }

    private void enforceLive() {
        if (state != State.RUNNING) throw new IllegalStateException("not running");
        if (!gate.hasActiveCellularCall()
                || SystemClock.elapsedRealtime() - heartbeatMs > HEARTBEAT_LIMIT_MS) {
            close();
            throw new IllegalStateException("call/heartbeat lost; adapter closed");
        }
    }

    /** Read exactly one 20ms frame, or return 0 if the device has no frame yet. */
    public synchronized int readDownlink(byte[] dst) throws Exception {
        enforceLive();
        if (dst == null || dst.length != FRAME_BYTES) throw new IllegalArgumentException("bad RX frame size");
        try {
            // Android's READ_NON_BLOCKING may return fewer than 20ms of PCM.
            // Assemble complete frames without leaking incomplete samples to STT.
            byte[] temp = new byte[FRAME_BYTES];
            int read = backend.read(temp);
            if (read < 0 || read > FRAME_BYTES) throw new IllegalStateException("bad read length: " + read);
            if (read == 0) return 0;
            int needed = FRAME_BYTES - rxPendingCount;
            int first = Math.min(read, needed);
            System.arraycopy(temp, 0, rxPending, rxPendingCount, first);
            rxPendingCount += first;
            if (rxPendingCount < FRAME_BYTES) return 0;
            System.arraycopy(rxPending, 0, dst, 0, FRAME_BYTES);
            rxFrames++;
            rxPendingCount = read - first;
            if (rxPendingCount > 0)
                System.arraycopy(temp, first, rxPending, 0, rxPendingCount);
            return FRAME_BYTES;
        } catch (Exception | Error e) {
            close();
            throw e;
        }
    }

    public synchronized void writeUplink(byte[] src) throws Exception {
        enforceLive();
        if (!approval.uplinkApproved) throw new SecurityException("uplink not approved");
        if (src == null || src.length != FRAME_BYTES) throw new IllegalArgumentException("bad TX frame size");
        try {
            if (backend.write(src) != FRAME_BYTES) throw new IllegalStateException("short uplink write");
            txFrames++;
        } catch (Exception | Error e) {
            close();
            throw e;
        }
    }

    @Override public synchronized void close() {
        if (state == State.CLOSED) return;
        state = State.CLOSED;
        heartbeatMs = -1L;
        rxPendingCount = 0;
        Arrays.fill(rxPending, (byte) 0);
        backend.close();
    }

    /**
     * No-call Shell UID/permission/identity gate. Shizuku UserService callers
     * must supply the underlying context (not the untrusted app context).
     */
    public static Context shellAudioContext(Context base) {
        if (base == null || Process.myUid() != SHELL_UID)
            throw new SecurityException("must run in Shizuku shell uid");
        if (base.checkPermission("android.permission.CALL_AUDIO_INTERCEPTION",
                Process.myPid(), Process.myUid()) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("CALL_AUDIO_INTERCEPTION missing");
        }
        if (base.checkPermission("android.permission.MODIFY_PHONE_STATE",
                Process.myPid(), Process.myUid()) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("MODIFY_PHONE_STATE missing");
        }
        final AttributionSource identity = new AttributionSource.Builder(SHELL_UID)
                .setPackageName(SHELL_PACKAGE).build();
        return new ContextWrapper(base) {
            @Override public String getPackageName() { return SHELL_PACKAGE; }
            @Override public String getOpPackageName() { return SHELL_PACKAGE; }
            @Override public AttributionSource getAttributionSource() { return identity; }
            @Override public Context getApplicationContext() { return this; }
            @Override public int getDeviceId() { return 0; }
        };
    }

    /** Fails closed if Android's call state cannot be established. */
    public static PhoneGate cellularGate(Context context) {
        return () -> {
            try {
                TelecomManager tm = context.getSystemService(TelecomManager.class);
                AudioManager am = context.getSystemService(AudioManager.class);
                return tm != null && tm.isInCall() && am != null
                        && (am.getMode() == AudioManager.MODE_IN_CALL);
            } catch (RuntimeException e) {
                return false;
            }
        };
    }

    /** Android helper-only backend; no direct VOICE_DOWNLINK/mic fallback. */
    public static final class AndroidRedirectBackend implements Backend {
        private final Context shellContext;
        private AudioRecord record;
        private AudioTrack track;

        public AndroidRedirectBackend(Context shellContext) { this.shellContext = shellContext; }

        @Override public void open() throws Exception {
            if (record != null || track != null) throw new IllegalStateException("already opened");
            Context checked = shellAudioContext(shellContext);
            AudioFormat rxFormat = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE_HZ).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
            AudioFormat txFormat = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE_HZ).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
            try {
                // Use explicit shell-identity AudioManager. Framework helper
                // may still reject default attribution on some Samsung builds:
                // such failures are intentional hard errors, never routed to MIC.
                Constructor<AudioManager> ctor = AudioManager.class.getDeclaredConstructor(Context.class);
                ctor.setAccessible(true);
                AudioManager mgr = ctor.newInstance(checked);
                Method rx = AudioManager.class.getMethod("getCallDownlinkExtractionAudioRecord", AudioFormat.class);
                Method tx = AudioManager.class.getMethod("getCallUplinkInjectionAudioTrack", AudioFormat.class);
                record = (AudioRecord) rx.invoke(mgr, rxFormat);
                track = (AudioTrack) tx.invoke(mgr, txFormat);
                if (record == null || track == null
                        || record.getState() != AudioRecord.STATE_INITIALIZED
                        || track.getState() != AudioTrack.STATE_INITIALIZED)
                    throw new IllegalStateException("call audio helper failed to initialize");
                record.startRecording();
                if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
                    throw new IllegalStateException("capture did not start");
                byte[] prefill = new byte[FRAME_BYTES * 2];
                if (track.write(prefill, 0, prefill.length, AudioTrack.WRITE_NON_BLOCKING) < 0)
                    throw new IllegalStateException("playback prefill failed");
                track.play();
                if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING)
                    throw new IllegalStateException("uplink did not start");
            } catch (Exception | Error e) {
                close();
                throw e;
            }
        }

        @Override public int read(byte[] target) {
            if (record == null) throw new IllegalStateException("capture not open");
            return record.read(target, 0, target.length, AudioRecord.READ_NON_BLOCKING);
        }
        @Override public int write(byte[] frame) {
            if (track == null) throw new IllegalStateException("injection not open");
            return track.write(frame, 0, frame.length, AudioTrack.WRITE_NON_BLOCKING);
        }
        @Override public void close() {
            if (record != null) {
                try { record.stop(); } catch (RuntimeException ignored) {}
                record.release(); record = null;
            }
            if (track != null) {
                try { track.pause(); track.flush(); track.stop(); } catch (RuntimeException ignored) {}
                track.release(); track = null;
            }
        }
    }
}
