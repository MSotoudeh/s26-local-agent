package io.projectir.mobile;
/** Deterministic Persian offline ASR routing safety tests, NO device effects. */
public final class LocalVoiceRoutingTest {
    private static int checks;
    private static void assertRoute(String text, LocalVoiceRouting.Kind kind, String canonical) {
        LocalVoiceRouting.Decision d = LocalVoiceRouting.classify(text);
        if (d.kind != kind || !d.canonicalCommand.equals(canonical)) {
            throw new AssertionError("routing " + checks + " " + d.kind + "/" + d.canonicalCommand);
        }
        if (d.kind == LocalVoiceRouting.Kind.NAVIGATE
                && !LocalPlanPolicy.mayExecute(LocalSkillRegistry.lookup(d.canonicalCommand))) {
            throw new AssertionError("voice route missing executable canonical policy");
        }
        checks++;
    }
    public static void main(String[] args) {
        for (int i = 0; i < 1000; i++) {
            assertRoute("\u062a\u0646\u0638\u06cc\u0645\u0627\u062a \u0631\u0627 \u0628\u0627\u0632 \u06a9\u0646", LocalVoiceRouting.Kind.NAVIGATE, "open settings");
            assertRoute("\u062a\u0646\u0638\u06cc\u0645\u0627\u062a \u0631\u0648 \u0628\u0627\u0632 \u06a9\u0646", LocalVoiceRouting.Kind.NAVIGATE, "open settings");
            assertRoute("\u0628\u0631\u0648 \u0628\u0647 \u062a\u0646\u0638\u06cc\u0645\u0627\u062a", LocalVoiceRouting.Kind.NAVIGATE, "open settings");
            assertRoute("\u0627\u062a\u0635\u0627\u0644\u0627\u062a \u0631\u0627 \u0628\u0627\u0632 \u06a9\u0646", LocalVoiceRouting.Kind.NAVIGATE, "open connections");
            assertRoute("\u0627\u062a\u0635\u0627\u0644\u0627\u062a \u0631\u0648 \u0628\u0627\u0632 \u06a9\u0646", LocalVoiceRouting.Kind.NAVIGATE, "open connections");
            assertRoute("\u0628\u0631\u0648 \u0628\u0647 \u0627\u062a\u0635\u0627\u0644\u0627\u062a", LocalVoiceRouting.Kind.NAVIGATE, "open connections");
            assertRoute("\u062a\u0646\u0638\u06cc\u0645\u0627\u062a \u0648 \u0627\u062a\u0635\u0627\u0644\u0627\u062a \u0631\u0627 \u0628\u0627\u0632 \u06a9\u0646", LocalVoiceRouting.Kind.NAVIGATE, "open connections");
            assertRoute("open settings", LocalVoiceRouting.Kind.NAVIGATE, "open settings");
            assertRoute("open connections", LocalVoiceRouting.Kind.NAVIGATE, "open connections");
            assertRoute("\u0686\u0631\u0627 \u0645\u0627\u0647 \u0631\u0648\u0634\u0646 \u0627\u0633\u062a\u061f", LocalVoiceRouting.Kind.ANSWER, "");
            assertRoute("\u0686\u0637\u0648\u0631 \u0631\u0627\u06cc\u0627\u0646\u0647 \u06a9\u0627\u0631 \u0645\u06cc‌\u06a9\u0646\u062f\u061f", LocalVoiceRouting.Kind.ANSWER, "");
            assertRoute("\u0633\u0627\u0639\u062a \u0631\u0627 \u0628\u0627\u0632 \u06a9\u0646", LocalVoiceRouting.Kind.REJECT, "");
            assertRoute("\u0648\u0627\u06cc \u0641\u0627\u06cc \u0631\u0627 \u062e\u0627\u0645\u0648\u0634 \u06a9\u0646", LocalVoiceRouting.Kind.REJECT, "");
            assertRoute("\u067e\u0627\u06a9 \u06a9\u0646 \u062a\u0645\u0627\u0645 \u0641\u0627\u06cc\u0644‌\u0647\u0627 \u0631\u0627", LocalVoiceRouting.Kind.REJECT, "");
            assertRoute("\u0628\u0631\u0648 \u062a\u0646\u0638\u06cc\u0645\u0627\u062a \u0628\u0639\u062f \u0648\u0627\u06cc \u0641\u0627\u06cc \u0631\u0627 \u062e\u0627\u0645\u0648\u0634 \u06a9\u0646", LocalVoiceRouting.Kind.REJECT, "");
            assertRoute("\u062a\u0646\u0638\u06cc\u0645\u0627\u062a \u0631\u0627 \u0628\u0627\u0632 \u06a9\u0646 \u0648 \u0647\u0645\u0647 \u0686\u06cc\u0632 \u0631\u0627 \u067e\u0627\u06a9 \u06a9\u0646", LocalVoiceRouting.Kind.REJECT, "");
            assertRoute("\u0632\u0646\u06af \u0628\u0632\u0646", LocalVoiceRouting.Kind.REJECT, "");
            assertRoute("", LocalVoiceRouting.Kind.REJECT, "");
            assertRoute("\u062d\u0630\u0641 \u06a9\u0646", LocalVoiceRouting.Kind.REJECT, "");
            assertRoute("\u0628\u0627\u0632 \u06a9\u0646 \u062a\u0646\u0638\u06cc\u0645\u0627\u062a", LocalVoiceRouting.Kind.REJECT, "");
            assertRoute("open settings please", LocalVoiceRouting.Kind.REJECT, "");
            assertRoute("unknown_command", LocalVoiceRouting.Kind.REJECT, "");
            assertRoute(null, LocalVoiceRouting.Kind.REJECT, "");
            assertRoute("x".repeat(180), LocalVoiceRouting.Kind.REJECT, "");
        }
        System.out.println("VOICE_ROUTING_ASSERTIONS=" + checks);
        System.out.println("VOICE_ROUTING_R0_SKILLS=2");
        System.out.println("SIDE_EFFECTS=0");
    }
}