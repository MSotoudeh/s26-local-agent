package io.projectir.mobile;

/**
 * Safety boundary from noisy offline ASR to the canonical plan executor.
 * No model/ASR text can invent package names, coordinates, selectors or steps.
 * EXACT phrase matching for the two real S26-verified R0 navigation skills.
 * Everything else either gets a read-only answer or fails closed.
 */
public final class LocalVoiceRouting {
    public enum Kind { NAVIGATE, ANSWER, REJECT }
    public static final class Decision {
        public final Kind kind;
        public final String canonicalCommand;
        public final String skillId;
        private Decision(Kind kind, String command, String skillId) {
            this.kind = kind;
            this.canonicalCommand = command;
            this.skillId = skillId;
        }
    }
    private LocalVoiceRouting() {}
    public static Decision classify(String spoken) {
        if (spoken == null || spoken.trim().length() == 0 || spoken.length() > 160) {
            return new Decision(Kind.REJECT, "", "");
        }
        String s = LocalSkillRegistry.normalize(spoken);
        if (s.length() < 4 || s.length() > 160) {
            return new Decision(Kind.REJECT, "", "");
        }
        switch (s) {
            case "\u062a\u0646\u0638\u06cc\u0645\u0627\u062a \u0631\u0627 \u0628\u0627\u0632 \u06a9\u0646":
            case "\u062a\u0646\u0638\u06cc\u0645\u0627\u062a \u0631\u0648 \u0628\u0627\u0632 \u06a9\u0646":
            case "\u062a\u0646\u0638\u06cc\u0645\u0627\u062a \u0631\u0627 \u0628\u0627\u0632 \u0628\u06a9\u0646":
            case "\u062a\u0646\u0638\u06cc\u0645\u0627\u062a \u0631\u0648 \u0628\u0627\u0632 \u0628\u06a9\u0646":
            case "\u0628\u0631\u0648 \u0628\u0647 \u062a\u0646\u0638\u06cc\u0645\u0627\u062a":
            case "\u062a\u0646\u0638\u06cc\u0645\u0627\u062a \u0631\u0627 \u0628\u0627\u0632\u06a9\u0646":
            case "open settings":
                return new Decision(Kind.NAVIGATE, "open settings", "android.settings.open");
            case "\u0627\u062a\u0635\u0627\u0644\u0627\u062a \u0631\u0627 \u0628\u0627\u0632 \u06a9\u0646":
            case "\u0627\u062a\u0635\u0627\u0644\u0627\u062a \u0631\u0648 \u0628\u0627\u0632 \u06a9\u0646":
            case "\u0627\u062a\u0635\u0627\u0644\u0627\u062a \u0631\u0627 \u0628\u0627\u0632 \u0628\u06a9\u0646":
            case "\u0627\u062a\u0635\u0627\u0644\u0627\u062a \u0631\u0648 \u0628\u0627\u0632 \u0628\u06a9\u0646":
            case "\u0628\u0631\u0648 \u0628\u0647 \u0627\u062a\u0635\u0627\u0644\u0627\u062a":
            case "\u062a\u0646\u0638\u06cc\u0645\u0627\u062a \u0627\u062a\u0635\u0627\u0644\u0627\u062a \u0631\u0627 \u0628\u0627\u0632 \u06a9\u0646":
            case "\u062a\u0646\u0638\u06cc\u0645\u0627\u062a \u0648 \u0627\u062a\u0635\u0627\u0644\u0627\u062a \u0631\u0627 \u0628\u0627\u0632 \u06a9\u0646":
            case "open connections":
            case "open settings connections":
                return new Decision(Kind.NAVIGATE, "open connections", "android.settings.connections");
            default:
                break;
        }
        // Open-ended queries may be answered as plain local text only; no
        // broad "best guess" device control or LLM-generated tool calls.
        boolean asksQuestion = spoken.contains("\u061f") || spoken.contains("?")
                || s.startsWith("\u0686\u06cc\u0633\u062a")
                || s.startsWith("\u0686\u06cc\u0647")
                || s.startsWith("\u0686\u0637\u0648\u0631")
                || s.startsWith("\u0686\u06af\u0648\u0646\u0647")
                || s.startsWith("\u0686\u0631\u0627")
                || s.startsWith("\u062a\u0648\u0636\u06cc\u062d \u0628\u062f\u0647")
                || s.startsWith("\u0628\u0647 \u0645\u0646 \u0628\u06af\u0648")
                || s.startsWith("\u0645\u06cc\u0634\u0647 \u062a\u0648\u0636\u06cc\u062d \u0628\u062f\u06cc")
                || s.startsWith("\u0686\u0647 \u062a\u0641\u0627\u0648\u062a\u06cc")
                || s.startsWith("\u06a9\u06cc")
                || s.startsWith("\u06a9\u062c\u0627");
        if (asksQuestion && !s.contains("\u0628\u0627\u0632 \u06a9\u0646")
                && !s.contains("\u067e\u0627\u06a9 \u06a9\u0646")
                && !s.contains("\u062d\u0630\u0641 \u06a9\u0646")
                && !s.contains("\u0632\u0646\u06af \u0628\u0632\u0646")) {
            return new Decision(Kind.ANSWER, "", "");
        }
        return new Decision(Kind.REJECT, "", "");
    }
}
