import java.util.Arrays;

/**
 * Device-side test entrypoint run by app_process as uid=shell.
 * Uses ONLY an in-memory fake; never instantiates AndroidRedirectBackend.
 * Commands: --self-test only. No dial/capture/inject functionality is exposed.
 */
public final class PstnAdapterSelfTest {
    private static int passed = 0;

    private static final class Fake implements PstnAndroidAdapter.Backend {
        boolean opened, closed, failOpen;
        int rx, tx;
        @Override public void open() {
            if (failOpen) throw new IllegalStateException("synthetic open failure");
            opened = true;
        }
        @Override public int read(byte[] b) {
            Arrays.fill(b, (byte) 0x2a);
            rx++;
            return b.length;
        }
        @Override public int write(byte[] b) { tx++; return b.length; }
        @Override public void close() { closed = true; }
    }
    private static final class Call implements PstnAndroidAdapter.PhoneGate {
        boolean active;
        Call(boolean isActive) { active = isActive; }
        @Override public boolean hasActiveCellularCall() { return active; }
    }
    private static void assertThat(boolean b, String s) {
        if (!b) throw new AssertionError(s);
    }
    private static void expectFailure(Checked op, String label) throws Exception {
        try { op.run(); throw new AssertionError("expected rejection: " + label); }
        catch (SecurityException | IllegalStateException | IllegalArgumentException expected) {
            passed++;
            System.out.println("PASS " + label);
        }
    }
    @FunctionalInterface private interface Checked { void run() throws Exception; }

    private static PstnAndroidAdapter.Authorization auth(boolean owner, boolean farEnd, boolean uplink) {
        return new PstnAndroidAdapter.Authorization(owner, farEnd, uplink);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !"--self-test".equals(args[0])) {
            System.err.println("Usage: PstnAdapterSelfTest --self-test (fake-only)");
            System.exit(2);
            return;
        }
        System.out.println("SCOPE=FAKE_BACKEND_ONLY_NO_CALL_NO_AUDIO");
        System.out.println("PHONE_AUDIO_BACKEND=NOT_OPENED");
        Fake f1 = new Fake();
        PstnAndroidAdapter a = new PstnAndroidAdapter(f1, new Call(true), auth(false, true, true));
        expectFailure(a::start, "owner_required");
        assertThat(!f1.opened, "did not open without owner");
        a.close();

        Fake f2 = new Fake();
        PstnAndroidAdapter b = new PstnAndroidAdapter(f2, new Call(true), auth(true, false, true));
        expectFailure(b::start, "participant_notice_required");
        assertThat(!f2.opened, "did not open without notice");
        b.close();

        Fake f3 = new Fake();
        PstnAndroidAdapter c = new PstnAndroidAdapter(f3, new Call(false), auth(true, true, true));
        expectFailure(c::start, "active_call_required");
        assertThat(!f3.opened, "did not open with inactive call");
        c.close();

        Fake f4 = new Fake();
        Call gate = new Call(true);
        PstnAndroidAdapter d = new PstnAndroidAdapter(f4, gate, auth(true, true, true));
        d.start();
        d.heartbeat();
        byte[] input = new byte[PstnAndroidAdapter.FRAME_BYTES];
        int n = d.readDownlink(input);
        assertThat(n == 640 && f4.rx == 1 && input[0] == 0x2a, "RX");
        d.writeUplink(input);
        assertThat(f4.tx == 1 && d.getRxFrames() == 1 && d.getTxFrames() == 1, "TX");
        passed++; System.out.println("PASS fake_pcm_20ms_round_trip");

        expectFailure(() -> d.writeUplink(new byte[1]), "bad_uplink_frame_rejected");
        gate.active = false;
        expectFailure(() -> d.readDownlink(input), "call_drop_closes_session");
        assertThat(d.getState() == PstnAndroidAdapter.State.CLOSED && f4.closed, "not closed on call end");
        passed++; System.out.println("PASS cleanup_after_call_end");

        Fake f5 = new Fake(); f5.failOpen = true;
        PstnAndroidAdapter e = new PstnAndroidAdapter(f5, new Call(true), auth(true, true, true));
        expectFailure(e::start, "open_failure_closes");
        assertThat(f5.closed && e.getState() == PstnAndroidAdapter.State.CLOSED, "cleanup failed");

        Fake f6 = new Fake();
        PstnAndroidAdapter g = new PstnAndroidAdapter(f6, new Call(true), auth(true, true, false));
        expectFailure(g::start, "uplink_approval_required");
        assertThat(!f6.opened, "could open uplink without approval");
        g.close();

        Fake f7 = new Fake();
        PstnAndroidAdapter h = new PstnAndroidAdapter(f7, new Call(true), auth(true, true, true));
        h.start();
        Thread.sleep(3500);
        assertThat(h.getState() == PstnAndroidAdapter.State.CLOSED && f7.closed, "watchdog did not close stream");
        passed++; System.out.println("PASS automatic_watchdog_close");
        System.out.println("ANDROID_ADAPTER_FAKE_TESTS_PASS=" + passed);
    }
}
