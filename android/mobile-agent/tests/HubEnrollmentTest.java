package io.projectir.mobile;

import java.security.SecureRandom;

/** Contract with the Hub's device_enrollment.ts; no network or device effects. */
public final class HubEnrollmentTest {
    private static int count = 0;
    private static void verify(boolean expected, String context) {
        if (!expected) throw new AssertionError(context);
        count++;
    }

    public static void main(String[] ignored) {
        String hex = "0123456789abcdef".repeat(4);
        String code = "pir1.s26." + hex + "." + hex;
        verify("s26".equals(HubEnrollment.deviceOf(code)), "device bound to code");
        verify("s26".equals(HubEnrollment.deviceOf("  " + code + "\n")), "pasted whitespace");
        verify(HubEnrollment.deviceOf(null) == null, "null code");
        verify(HubEnrollment.deviceOf(code + "0") == null, "long mac");
        verify(HubEnrollment.deviceOf("pir1.S26." + hex + "." + hex) == null, "uppercase device");
        verify(HubEnrollment.deviceOf("pir1.s26." + hex) == null, "missing mac");
        verify(HubEnrollment.looksLikeCode("pir1.broken"), "malformed code is still a code");
        verify(!HubEnrollment.looksLikeCode(hex), "raw token is not a code");
        verify(!HubEnrollment.validDeviceId("s2"), "Hub requires three characters");
        verify(!HubEnrollment.validDeviceId("a.b-c"), "Hub rejects dots");

        SecureRandom random = new SecureRandom();
        String token = HubEnrollment.newToken(random);
        verify(HubEnrollment.validToken(token), "token matches the Hub format");
        verify(!token.equals(HubEnrollment.newToken(random)), "tokens are unique");
        verify(HubEnrollment.requestBody(code, hex).equals("{\"device_id\":\"s26\",\"enrollment_code\":\""
                + code + "\",\"agent_token\":\"" + hex + "\"}"), "request body");
        boolean refused = false;
        try {
            HubEnrollment.requestBody(code, "\"},{");
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        verify(refused, "unvalidated token never reaches the body");

        verify(HubEnrollment.classify(200) == HubEnrollment.Outcome.ENROLLED, "200");
        verify(HubEnrollment.classify(401) == HubEnrollment.Outcome.VERIFY, "lost reply is checked");
        verify(HubEnrollment.classify(429) == HubEnrollment.Outcome.RETRY, "rate limit");
        verify(HubEnrollment.classify(503) == HubEnrollment.Outcome.RETRY, "not configured yet");
        verify(HubEnrollment.classify(400) == HubEnrollment.Outcome.REJECTED, "bad request");
        System.out.println("HUB_ENROLLMENT_PASS=" + count);
    }
}
