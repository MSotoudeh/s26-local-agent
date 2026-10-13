package io.projectir.mobile;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

/**
 * Fixed offline skill lookup. Candidate plans are data; no model gets to
 * provide a package name, coordinate, shell command or executable step.
 */
public final class LocalSkillRegistry {
    private static final String SETTINGS = "com.android.settings";
    private LocalSkillRegistry() {}

    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace('\u064a', '\u06cc')
                .replace('\u0643', '\u06a9')
                .replace('\u200c', ' ')
                .replaceAll("[\\u064b-\\u065f\\u0670]", "")
                .replaceAll("[.!?؟،,:;]+", " ")
                .trim().replaceAll("\\s+", " ");
        return s;
    }

    public static LocalSkillPlan lookup(String raw) {
        String n = normalize(raw);
        if (n.isEmpty()) return null;
        switch (n) {
            case "بررسی وضعیت باتری":
            case "مصرف باتری را بررسی کن":
            case "مصرف باتری رو بررسی کن":
            case "check battery usage":
            case "show battery status":
                return batteryCandidate();
            case "تنظیمات را باز کن و وارد connections شو":
            case "تنظیمات رو باز کن و وارد connections شو":
            case "open settings connections":
            case "open connections":
                return connectionsVerified();
            case "تنظیمات را باز کن":
            case "تنظیمات رو باز کن":
            case "open settings":
                return openApp("android.settings.open", SETTINGS, true);
            case "ماشین حساب را باز کن":
            case "ماشین حساب رو باز کن":
            case "open calculator":
                return openApp("samsung.calculator.open",
                        "com.sec.android.app.popupcalculator", true);
            case "ساعت را باز کن":
            case "ساعت رو باز کن":
            case "open clock":
                return openApp("samsung.clock.open", "com.sec.android.app.clockpackage", true);
            case "نقشه را باز کن":
            case "نقشه رو باز کن":
            case "open maps":
                return openApp("google.maps.open", "com.google.android.apps.maps", true);
            default:
                return null;
        }
    }

    /** Canonical skill lookup; untrusted proposals may not invent new steps. */
    public static LocalSkillPlan canonicalById(String id) {
        if (id == null) return null;
        switch (id) {
            case "android.settings.open":
                return openApp("android.settings.open", SETTINGS, true);
            case "samsung.calculator.open":
                return openApp("samsung.calculator.open",
                        "com.sec.android.app.popupcalculator", true);
            case "samsung.clock.open":
                return openApp("samsung.clock.open",
                        "com.sec.android.app.clockpackage", true);
            case "google.maps.open":
                return openApp("google.maps.open", "com.google.android.apps.maps", true);
            case "android.settings.connections":
                return connectionsVerified();
            case "samsung.settings.battery.read":
                return batteryCandidate();
            default:
                return null;
        }
    }

    private static LocalSkillPlan openApp(String id, String pkg, boolean validated) {
        return new LocalSkillPlan(id, LocalSkillPlan.Risk.R0_READ_ONLY,
                validated, "Previously tested application launch",
                Arrays.asList(
                        new LocalSkillPlan.Step(LocalSkillPlan.StepKind.LAUNCH,
                                pkg, "", 3000),
                        new LocalSkillPlan.Step(LocalSkillPlan.StepKind.AWAIT_PACKAGE,
                                pkg, "", 4000),
                        new LocalSkillPlan.Step(LocalSkillPlan.StepKind.REPORT,
                                pkg, "", 1000)
                ));
    }

    private static LocalSkillPlan connectionsVerified() {
        return new LocalSkillPlan("android.settings.connections",
                LocalSkillPlan.Risk.R0_READ_ONLY, true,
                "Samsung Settings > Connections previously verified on S26",
                Arrays.asList(
                        new LocalSkillPlan.Step(LocalSkillPlan.StepKind.LAUNCH,
                                SETTINGS, "", 3000),
                        new LocalSkillPlan.Step(LocalSkillPlan.StepKind.AWAIT_PACKAGE,
                                SETTINGS, "", 4000),
                        new LocalSkillPlan.Step(LocalSkillPlan.StepKind.CLICK_UNIQUE_TEXT,
                                SETTINGS, "Connections", 2000),
                        new LocalSkillPlan.Step(LocalSkillPlan.StepKind.VERIFY_TEXTS,
                                SETTINGS, "", 4000, "Connections", "Wi-Fi", "Bluetooth"),
                        new LocalSkillPlan.Step(LocalSkillPlan.StepKind.REPORT,
                                SETTINGS, "", 1000)
                ));
    }

    private static LocalSkillPlan batteryCandidate() {
        return new LocalSkillPlan("samsung.settings.battery.read",
                LocalSkillPlan.Risk.R0_READ_ONLY, false,
                "Candidate route; verify One UI battery navigation before enabling",
                Arrays.asList(
                        new LocalSkillPlan.Step(LocalSkillPlan.StepKind.LAUNCH,
                                SETTINGS, "", 3000),
                        new LocalSkillPlan.Step(LocalSkillPlan.StepKind.AWAIT_PACKAGE,
                                SETTINGS, "", 4000),
                        new LocalSkillPlan.Step(LocalSkillPlan.StepKind.CLICK_UNIQUE_TEXT,
                                SETTINGS, "Battery", 2000),
                        new LocalSkillPlan.Step(LocalSkillPlan.StepKind.VERIFY_TEXTS,
                                SETTINGS, "", 4000, "Battery"),
                        new LocalSkillPlan.Step(LocalSkillPlan.StepKind.REPORT,
                                SETTINGS, "", 1000)
                ));
    }

    public static LocalSkillPlan emptyPreview() {
        return new LocalSkillPlan("unsupported", LocalSkillPlan.Risk.R3_SENSITIVE,
                false, "No verified local skill matches the exact request",
                Collections.singletonList(new LocalSkillPlan.Step(
                        LocalSkillPlan.StepKind.REPORT, "", "", 1000)));
    }
}
