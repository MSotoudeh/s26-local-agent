package io.projectir.mobile;

import java.security.SecureRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Per-device Hub enrollment contract (POST /agent/enroll).
 *
 * The phone generates its own bearer token and registers it with a one-time
 * code that the Hub bound to this device ID. The shared Ops agent token is
 * never copied to the phone: the Hub accepts it for device "ops" only.
 *
 * Plain Java so the contract can be regression-tested on the host JVM.
 */
final class HubEnrollment {
    private static final Pattern DEVICE = Pattern.compile("[a-z][a-z0-9-]{2,63}");
    private static final Pattern CODE =
            Pattern.compile("pir1\\.([a-z][a-z0-9-]{2,63})\\.[a-f0-9]{64}\\.[a-f0-9]{64}");
    private static final Pattern TOKEN = Pattern.compile("[a-f0-9]{64}");

    enum Outcome {
        /** The Hub registered the token. */
        ENROLLED,
        /** Refused; an earlier attempt may have used the code and lost the reply. */
        VERIFY,
        /** Temporary failure; keep the pending enrollment and try again later. */
        RETRY,
        /** The request itself is wrong; retrying cannot help. */
        REJECTED
    }

    private HubEnrollment() {}

    /** True for anything meant as a code, so a malformed code is never saved as a token. */
    static boolean looksLikeCode(String value) {
        return value != null && value.trim().startsWith("pir1.");
    }

    static boolean validDeviceId(String value) {
        return value != null && DEVICE.matcher(value).matches();
    }

    static boolean validToken(String value) {
        return value != null && TOKEN.matcher(value).matches();
    }

    /** Device ID the code is bound to, or null when the code is malformed. */
    static String deviceOf(String code) {
        if (code == null) return null;
        Matcher matcher = CODE.matcher(code.trim());
        return matcher.matches() ? matcher.group(1) : null;
    }

    static String newToken(SecureRandom random) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        StringBuilder out = new StringBuilder(64);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xf, 16));
            out.append(Character.forDigit(b & 0xf, 16));
        }
        return out.toString();
    }

    /** Request body. Every field matches a JSON-safe pattern, so no escaping is needed. */
    static String requestBody(String code, String token) {
        String device = deviceOf(code);
        if (device == null || !validToken(token)) {
            throw new IllegalArgumentException("invalid enrollment");
        }
        return "{\"device_id\":\"" + device + "\",\"enrollment_code\":\"" + code.trim()
                + "\",\"agent_token\":\"" + token + "\"}";
    }

    static Outcome classify(int httpStatus) {
        if (httpStatus >= 200 && httpStatus < 300) return Outcome.ENROLLED;
        if (httpStatus == 401 || httpStatus == 403) return Outcome.VERIFY;
        if (httpStatus == 400) return Outcome.REJECTED;
        return Outcome.RETRY;
    }
}
