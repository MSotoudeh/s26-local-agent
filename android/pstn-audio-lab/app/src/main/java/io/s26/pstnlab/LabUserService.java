package io.s26.pstnlab;

import android.content.Context;
import android.os.Process;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Real Shizuku UserService running under shell UID, but fake PCM only.
 * No network, telephony command, recording, microphone, injection, or audio HAL.
 */
public final class LabUserService extends ILabService.Stub {
    private final Context context;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    public LabUserService() { context = null; }
    public LabUserService(Context context) { this.context = context; }
    @Override public String status() {
        JSONObject o = new JSONObject();
        try {
            o.put("service", "shizuku_userservice");
            o.put("uid", Process.myUid());
            o.put("shell", Process.myUid() == 2000);
            o.put("stopped", stopped.get());
            o.put("phone_audio_backend", "disabled");
            o.put("adapter", "loaded_not_opened");
            o.put("call", "not_attempted");
        } catch (Exception ignored) { }
        return o.toString();
    }

    @Override public synchronized String fakePcmTest() {
        JSONObject result = new JSONObject();
        try {
            if (stopped.get()) throw new IllegalStateException("service stopped");
            if (Process.myUid() != 2000) throw new SecurityException("not Shizuku shell");
            final class Fake implements PstnAndroidAdapter.Backend {
                int rx, tx; boolean started, closed;
                @Override public void open() { started = true; }
                @Override public int read(byte[] data) {
                    if (!started || closed) throw new IllegalStateException("fake not open");
                    Arrays.fill(data, (byte)0x17); rx++; return data.length;
                }
                @Override public int write(byte[] data) {
                    if (!started || closed) throw new IllegalStateException("fake not open");
                    if (data.length != 640) throw new IllegalArgumentException("fake frame mismatch");
                    tx++; return data.length;
                }
                @Override public void close() { closed = true; started = false; }
            }
            Fake fake = new Fake();
            PstnAndroidAdapter.PhoneGate fakeCall = () -> true;
            PstnAndroidAdapter adapter = new PstnAndroidAdapter(fake, fakeCall,
                    new PstnAndroidAdapter.Authorization(true, true, true));
            try {
                adapter.start();
                for (int i = 0; i < 100; i++) {
                    byte[] recv = new byte[PstnAndroidAdapter.FRAME_BYTES];
                    int count = adapter.readDownlink(recv);
                    if (count != PstnAndroidAdapter.FRAME_BYTES || recv[0] != (byte)0x17)
                        throw new IllegalStateException("fake RX mismatch");
                    adapter.writeUplink(recv);
                    adapter.heartbeat();
                }
                result.put("rx", adapter.getRxFrames());
                result.put("tx", adapter.getTxFrames());
            } finally { adapter.close(); }
            result.put("cleanup", fake.closed);
            result.put("uid", Process.myUid());
            result.put("backend", "FAKE_ONLY");
            result.put("real_call", "NOT_USED");
            result.put("pass", fake.rx == 100 && fake.tx == 100 && fake.closed);
        } catch (Throwable e) {
            try { result.put("pass", false); result.put("error", e.getClass().getSimpleName()); }
            catch (Exception ignored) { }
        }
        return result.toString();
    }

    /** The Binder PCM channel is intentionally synthetic and strictly frame-bounded. */
    @Override public synchronized byte[] fakePcmFrame(byte[] frame) {
        if (stopped.get() || Process.myUid() != 2000) throw new SecurityException("lab not active under shell");
        if (frame == null || frame.length != PstnAndroidAdapter.FRAME_BYTES)
            throw new IllegalArgumentException("only 20ms / 16k mono PCM16 frame allowed");
        // Return the in-memory frame. No AudioRecord or AudioTrack is instantiated.
        return Arrays.copyOf(frame, frame.length);
    }

    @Override public void destroy() {
        stopped.set(true);
        System.exit(0);
    }
}
