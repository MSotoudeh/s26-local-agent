package io.projectir.mobile;

import java.util.Arrays;
import java.util.Collections;

/** No Android device effect. Verifies settling and fail-closed transitions. */
public final class LocalObservationGateTest {
    private static int checks;

    private static void ok(boolean condition, String label) {
        if (!condition) throw new AssertionError(label + " #" + checks);
        checks++;
    }

    private static LocalPlanMachine.Observation observe(String id, String pkg, boolean truncated) {
        return new LocalPlanMachine.Observation(id, pkg,
                Arrays.asList("Connections", "Wi-Fi", "Bluetooth"),
                Collections.emptyList(), truncated);
    }

    private static void test(int i) {
        LocalObservationGate gate = new LocalObservationGate();
        LocalPlanMachine.Observation a = observe("state-alpha-001", "com.android.settings", false);
        LocalPlanMachine.Observation b = observe("state-bravo-002", "com.android.settings", false);
        ok(gate.accept(a, 100L) == null, "young snapshot rejected");
        ok(gate.accept(a, 250L) == null, "first settled observation held");
        ok(gate.accept(a, 300L) != null, "second settled observation accepted");
        ok(gate.accept(a, 500L) != null, "third identical accepted");
        ok(gate.accept(b, 500L) == null, "new digest requires stability");
        ok(gate.accept(b, 500L) != null, "new digest becomes stable");
        ok(gate.accept(b, 120L) == null, "fresh Accessibility event resets");
        ok(gate.accept(b, 500L) == null, "first after reset held");
        ok(gate.accept(b, 500L) != null, "second after reset accepted");
        ok(gate.accept(observe("state-bravo-002", "com.other", false), 500L) == null,
                "package transition resets");
        ok(gate.accept(b, 500L) == null, "returning package also unsettled");
        ok(gate.accept(b, 500L) != null, "returning package settles");
        ok(gate.accept(observe("state-bravo-002", "com.android.settings", true), 500L) == null,
                "incomplete tree rejected");
        ok(gate.accept(b, 500L) == null, "first after truncation rejected");
        ok(gate.accept(b, 500L) != null, "second after truncation accepted");
        ok(gate.accept(null, 500L) == null, "missing observation clears gate");
        ok(gate.accept(b, 500L) == null, "missing previous state prevents reuse");
        ok(gate.accept(b, 500L) != null, "stabilized afterward");
        gate.reset();
        ok(gate.accept(b, 500L) == null, "explicit reset works");
        ok(gate.accept(b, 500L) != null, "explicit reset requires second read");
    }

    public static void main(String[] args) {
        for (int i = 0; i < 1000; i++) test(i);
        System.out.println("OBSERVATION_GATE_FIXTURES=1000");
        System.out.println("OBSERVATION_GATE_ASSERTIONS=" + checks);
        System.out.println("OBSERVATION_GATE_PHONE_EFFECTS=0");
    }
}
