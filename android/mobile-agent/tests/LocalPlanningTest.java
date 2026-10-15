package io.projectir.mobile;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Host JVM validation; no phone permissions, Shizuku or cloud required. */
public final class LocalPlanningTest {
    private static int tests = 0;
    private static void ok(boolean value, String reason) {
        if (!value) throw new AssertionError("Test " + tests + ": " + reason);
        tests++;
    }

    private static LocalSkillPlan.Step step(LocalSkillPlan.StepKind kind, String pkg,
                                            String text, int timeout, String... labels) {
        return new LocalSkillPlan.Step(kind, pkg, text, timeout, labels);
    }

    public static void main(String[] args) {
        LocalSkillPlan c = LocalSkillRegistry.lookup("تنظیمات را باز کن و وارد Connections شو");
        ok(c != null, "connections parsed");
        ok(c.liveValidated, "connections previously verified");
        ok(c.risk == LocalSkillPlan.Risk.R0_READ_ONLY, "risk class");
        ok(LocalPlanPolicy.mayExecute(c), "connections within policy");
        ok(c.steps.size() == 5, "bounded plan length");
        ok(c.steps.get(2).selector.equals("Connections"), "unique selector");
        ok(c.steps.get(3).assertions.contains("Wi-Fi"), "postcondition");
        ok(c.preview().contains("preview does not execute"), "preview no side effect");
        ok(LocalSkillRegistry.lookup("تنظيمات رو باز كن و وارد connections شو") != null, "Persian folding");
        ok(LocalSkillRegistry.lookup("OPEN CONNECTIONS!") != null, "Unicode English folding");

        LocalSkillPlan battery = LocalSkillRegistry.lookup("بررسی وضعیت باتری");
        ok(battery != null, "candidate registered");
        ok(!battery.liveValidated, "candidate not live proven");
        ok(LocalPlanPolicy.rejection(battery) == null, "shape valid");
        ok(!LocalPlanPolicy.mayExecute(battery), "blocked from execution");
        ok(battery.preview().contains("HOLD"), "HOLD visible");
        ok(LocalSkillRegistry.lookup("check battery usage") != null, "English battery synonym");
        ok(LocalSkillRegistry.lookup("حذف همه پیام‌ها") == null, "delete refused");
        ok(LocalSkillRegistry.lookup("send message to a contact") == null, "message refused");
        ok(LocalSkillRegistry.lookup("open settings; rm -rf /") == null, "compound refused");
        ok(LocalSkillRegistry.lookup("") == null, "empty refused");
        ok(LocalSkillRegistry.lookup(null) == null, "null refused");
        ok(LocalSkillRegistry.lookup("open maps") != null, "maps known");
        ok(LocalSkillRegistry.lookup("open clock") != null, "clock known");
        ok(LocalSkillRegistry.lookup("open calculator") != null, "calculator known");

        LocalSkillPlan shell = new LocalSkillPlan("unsafe.shell", LocalSkillPlan.Risk.R0_READ_ONLY,
                true, "", Arrays.asList(step(LocalSkillPlan.StepKind.LAUNCH,
                "com.malicious.app", "", 1000), step(LocalSkillPlan.StepKind.REPORT, "", "", 1000)));
        ok("PACKAGE_NOT_ALLOWED".equals(LocalPlanPolicy.rejection(shell)), "unknown package rejected");
        LocalSkillPlan missing = new LocalSkillPlan("a.test", LocalSkillPlan.Risk.R0_READ_ONLY,
                true, "", Arrays.asList(step(LocalSkillPlan.StepKind.LAUNCH, "com.android.settings", "", 1000)));
        ok("MISSING_FINAL_REPORT".equals(LocalPlanPolicy.rejection(missing)), "terminal proof");
        LocalSkillPlan huge = new LocalSkillPlan("a.test", LocalSkillPlan.Risk.R0_READ_ONLY,
                true, "", Collections.nCopies(9,
                step(LocalSkillPlan.StepKind.REPORT, "com.android.settings", "", 1000)));
        ok("UNBOUNDED_PLAN".equals(LocalPlanPolicy.rejection(huge)), "step bound");
        LocalSkillPlan risky = new LocalSkillPlan("a.test", LocalSkillPlan.Risk.R3_SENSITIVE,
                true, "", c.steps);
        ok(!LocalPlanPolicy.mayExecute(risky), "R3 stopped");
        LocalSkillPlan timeout = new LocalSkillPlan("a.test", LocalSkillPlan.Risk.R0_READ_ONLY,
                true, "", Arrays.asList(step(LocalSkillPlan.StepKind.LAUNCH, "com.android.settings", "", 5001),
                step(LocalSkillPlan.StepKind.REPORT, "", "", 1000)));
        ok("UNBOUNDED_TIMEOUT".equals(LocalPlanPolicy.rejection(timeout)), "timeout guard");
        LocalSkillPlan unknown = new LocalSkillPlan("made.up.workflow",
                LocalSkillPlan.Risk.R0_READ_ONLY, true, "",
                Arrays.asList(step(LocalSkillPlan.StepKind.LAUNCH,
                        "com.android.settings", "", 1000),
                        step(LocalSkillPlan.StepKind.REPORT, "", "", 1000)));
        ok("UNREGISTERED_SKILL".equals(LocalPlanPolicy.rejection(unknown)),
                "unknown R0 skill does not become executable");
        LocalSkillPlan forgedBattery = new LocalSkillPlan(battery.skillId,
                battery.risk, true, "forged live validation", battery.steps);
        ok("MUTATED_SKILL".equals(LocalPlanPolicy.rejection(forgedBattery)),
                "unverified battery route cannot be promoted");
        List<LocalSkillPlan.Step> altered = new java.util.ArrayList<>(c.steps);
        altered.set(2, step(LocalSkillPlan.StepKind.CLICK_UNIQUE_TEXT,
                "com.android.settings", "Delete", 2000));
        LocalSkillPlan forgedClick = new LocalSkillPlan(c.skillId, c.risk,
                true, "untrusted selector", altered);
        ok("MUTATED_SKILL".equals(LocalPlanPolicy.rejection(forgedClick)),
                "changing a validated selector is refused");

        String state = "state-v1-001";
        String pkg = "com.android.settings";
        LocalSemanticSelector.Node found = new LocalSemanticSelector.Node(7,pkg,"Connections",true,true);
        List<LocalSemanticSelector.Node> visible = Arrays.asList(
                new LocalSemanticSelector.Node(1,pkg,"Settings",true,false),found);
        LocalSemanticSelector.Decision good=LocalSemanticSelector.decide(
                state,state,pkg,pkg,"connections",visible,false);
        ok(good.accepted && good.nodeId == 7, "exact action");
        ok(!LocalSemanticSelector.decide(state,"state-v1-002",pkg,pkg,"Connections",visible,false).accepted,
                "stale snapshot blocked");
        ok(!LocalSemanticSelector.decide(state,state,pkg,"com.other.app","Connections",visible,false).accepted,
                "package switched");
        ok(!LocalSemanticSelector.decide(state,state,pkg,pkg,"Connections",visible,true).accepted,
                "truncated tree");
        ok(!LocalSemanticSelector.decide(state,state,pkg,pkg,"Bluetooth",visible,false).accepted,
                "target not present");
        List<LocalSemanticSelector.Node> duplicates = Arrays.asList(found,
                new LocalSemanticSelector.Node(8,pkg,"Connections",true,true));
        ok("AMBIGUOUS_TARGET".equals(LocalSemanticSelector.decide(
                state,state,pkg,pkg,"Connections",duplicates,false).reason),
                "ambiguous target");
        List<LocalSemanticSelector.Node> duplicateId = Arrays.asList(found,
                new LocalSemanticSelector.Node(7,pkg,"Other",true,true));
        ok("INVALID_NODE_TREE".equals(LocalSemanticSelector.decide(
                state,state,pkg,pkg,"Connections",duplicateId,false).reason), "duplicate IDs");
        List<LocalSemanticSelector.Node> disabled=Collections.singletonList(
                new LocalSemanticSelector.Node(2,pkg,"Connections",false,true));
        ok("TARGET_NOT_ACTIONABLE".equals(LocalSemanticSelector.decide(
                state,state,pkg,pkg,"Connections",disabled,false).reason), "disabled target");
        ok("STALE_STATE".equals(LocalSemanticSelector.decide(
                "short","short",pkg,pkg,"Connections",visible,false).reason), "weak state id");

        // Adversarial grammar: the static registry cannot execute injected suffixes.
        String[] adversarial={
            "open calculator then send a message",
            "open maps && delete files",
            "بررسی وضعیت باتری و پاک کردن اطلاعات",
            "connect to unknown bank app",
            "open settings\nexecute shell",
            "install app and grant permission",
            "run locally with root access"
        };
        for (String s: adversarial) ok(LocalSkillRegistry.lookup(s) == null,"reject injection");
        System.out.println("LOCAL_PLANNING_TESTS_PASS=" + tests);
    }
}
