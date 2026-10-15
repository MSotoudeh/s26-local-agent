package io.projectir.mobile;

import java.util.Locale;

/** A deliberately narrow, deterministic, offline command grammar. */
public final class LocalCommandRouter {
    public enum Kind {
        OPEN_SETTINGS, OPEN_CONNECTIONS, OPEN_CALCULATOR, OPEN_CLOCK,
        OPEN_MAPS, DEVICE_STATUS, WAIT_FIVE_SECONDS, UNSUPPORTED
    }

    private LocalCommandRouter() {}

    public static Kind parse(String raw) {
        if (raw == null) return Kind.UNSUPPORTED;
        String s = raw.toLowerCase(Locale.ROOT)
                .replace('\u064a', '\u06cc').replace('\u0643', '\u06a9')
                .replace('\u200c', ' ').replaceAll("[\\p{M}]", "")
                .replaceAll("[.!؟،,]+", " ").trim().replaceAll("\\s+", " ");
        switch (s) {
            case "تنظیمات را باز کن":
            case "تنظیمات رو باز کن":
            case "باز کردن تنظیمات":
            case "open settings":
                return Kind.OPEN_SETTINGS;
            case "تنظیمات را باز کن و وارد connections شو":
            case "تنظیمات رو باز کن و وارد connections شو":
            case "وارد اتصالات شو":
            case "open settings connections":
            case "open connections":
                return Kind.OPEN_CONNECTIONS;
            case "ماشین حساب را باز کن":
            case "ماشین حساب رو باز کن":
            case "open calculator":
                return Kind.OPEN_CALCULATOR;
            case "ساعت را باز کن":
            case "ساعت رو باز کن":
            case "open clock":
                return Kind.OPEN_CLOCK;
            case "نقشه را باز کن":
            case "نقشه رو باز کن":
            case "open maps":
                return Kind.OPEN_MAPS;
            case "پنج ثانیه صبر کن":
            case "wait five seconds":
                return Kind.WAIT_FIVE_SECONDS;
            case "وضعیت گوشی":
            case "وضعیت را بگو":
            case "device status":
                return Kind.DEVICE_STATUS;
            default:
                return Kind.UNSUPPORTED;
        }
    }
}
