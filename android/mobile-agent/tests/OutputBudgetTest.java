package io.projectir.mobile;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class OutputBudgetTest {
    private static int count = 0;
    private static void verify(boolean expected, String context) {
        if (!expected) throw new AssertionError(context);
        count++;
    }

    public static void main(String[] ignored) {
        OutputBudget small = OutputBudget.fit("ok", "warn", 100);
        verify(small.stdout.equals("ok") && small.stderr.equals("warn"), "small output unchanged");
        verify(!small.stdoutCut && !small.stderrCut, "small output not marked");

        String big = "x".repeat(1_000_000);
        OutputBudget both = OutputBudget.fit(big, big, 400_000);
        verify(both.stdoutCut && both.stderrCut, "both marked");
        verify(both.stdout.length() == 300_000 && both.stderr.length() == 100_000,
                "stdout keeps three quarters");
        OutputBudget errOnly = OutputBudget.fit("", big, 400_000);
        verify(errOnly.stderr.length() == 400_000 && !errOnly.stdoutCut, "stderr may use free room");
        OutputBudget outOnly = OutputBudget.fit(big, "warn", 400_000);
        verify(outOnly.stdout.length() == 399_996 && !outOnly.stderrCut, "stdout may use free room");

        String control = String.valueOf((char) 27).repeat(200_000);
        OutputBudget escaped = OutputBudget.fit(control, control, 400_000);
        verify(OutputBudget.jsonBytes(escaped.stdout) + OutputBudget.jsonBytes(escaped.stderr) <= 400_000,
                "escaping is counted");
        verify(OutputBudget.jsonBytes("\"/\\\n") == 8, "two-byte escapes");
        verify(OutputBudget.jsonBytes("سلام") == 8, "Persian is two bytes per letter");

        verify(OutputBudget.prefix("a😀b", 4).equals("a"), "surrogate pair is not split");
        byte[] persian = "سلام".getBytes(StandardCharsets.UTF_8);
        verify(OutputBudget.decode(Arrays.copyOf(persian, 7), true).equals("سلا"), "half letter dropped");
        verify(OutputBudget.decode(persian, true).equals("سلام"), "whole letters kept");
        verify(OutputBudget.decode(new byte[0], true).isEmpty(), "empty");
        byte[] emoji = "a😀".getBytes(StandardCharsets.UTF_8);
        verify(OutputBudget.decode(Arrays.copyOf(emoji, 4), true).equals("a"), "half emoji dropped");
        verify(OutputBudget.decode(emoji, true).equals("a😀"), "whole emoji kept");
        System.out.println("OUTPUT_BUDGET_PASS=" + count);
    }
}
