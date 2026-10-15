package io.projectir.mobile;

import java.util.Arrays;
import java.util.Collections;

/** No device/hub/test bridge required. All action effects are simulated. */
public final class LocalPlanMachineTest {
    private static int checks;
    private static void ok(boolean actual, String description) {
        if (!actual) throw new AssertionError("#" + checks + " " + description);
        checks++;
    }

    private static LocalPlanMachine.Observation settings() {
        String pkg = "com.android.settings";
        return new LocalPlanMachine.Observation(
                "state-0001", pkg,
                Arrays.asList("Settings", "Connections", "Connected devices"),
                Arrays.asList(
                        new LocalSemanticSelector.Node(1, pkg, "Settings", true, false),
                        new LocalSemanticSelector.Node(2, pkg, "Connections", true, true),
                        new LocalSemanticSelector.Node(3, pkg, "Connected devices", true, true)),
                false);
    }

    private static LocalPlanMachine.Observation destination() {
        String pkg = "com.android.settings";
        return new LocalPlanMachine.Observation("state-0002", pkg,
                Arrays.asList("Connections","Wi-Fi","Bluetooth"),
                Collections.emptyList(),false);
    }

    private static LocalPlanMachine fresh(int i) {
        return new LocalPlanMachine(LocalSkillRegistry.lookup("open connections"),
                String.format("task_%08d", i));
    }

    private static void success(int i) {
        LocalPlanMachine machine = fresh(i);
        LocalPlanMachine.Event launch = machine.advance(null, 0);
        ok(launch.kind == LocalPlanMachine.EventKind.DISPATCH, "launch dispatched");
        ok("task_".concat(String.format("%08d",i)).concat("_1").equals(
                launch.request.requestId), "stable request id");
        ok(machine.advance(null, 200).kind == LocalPlanMachine.EventKind.WAIT,
                "no duplicate dispatch without ACK");
        ok(machine.acknowledge(launch.request.requestId, "", true, 250),
                "launch ACK");
        ok(!machine.acknowledge(launch.request.requestId, "", true, 251),
                "duplicate ACK ignored");
        ok(machine.advance(settings(),300).stepIndex==2,"package verified");
        LocalPlanMachine.Event click=machine.advance(settings(),310);
        ok(click.kind == LocalPlanMachine.EventKind.DISPATCH, "unique click selected");
        ok(click.request.selectedNodeId==2, "unique target ID");
        ok("state-0001".equals(click.request.expectedStateId), "state pinned");
        ok(machine.advance(settings(),320).request==null,"no action replay");
        ok(machine.acknowledge(click.request.requestId, "state-0001", true,330),
                "click ACK");
        ok(machine.advance(destination(), 500).stepIndex==4,
                "explicit destination assertions");
        LocalPlanMachine.Event done=machine.advance(destination(),520);
        ok(done.kind == LocalPlanMachine.EventKind.FINISHED
                && machine.state()==LocalPlanMachine.State.SUCCEEDED,"success");
        ok(machine.advance(destination(),530).request==null,"terminal stable");
        ok(!machine.cancel(),"cannot cancel completed job");
    }

    private static void failures() {
        LocalPlanMachine m=fresh(99901);
        LocalPlanMachine.Event request=m.advance(null,0);
        ok(!m.acknowledge("wrong-id","",true,10),"mismatched ACK rejected");
        ok(m.advance(null,3000).kind == LocalPlanMachine.EventKind.FINISHED,
                "ACK expiration fails without replay");
        ok("ACK_TIMEOUT_NO_RETRY".equals(m.reason()),"correct timeout");
        ok(m.advance(null,4000).request==null,"timeout terminal no replay");
        ok(!m.acknowledge(request.request.requestId,"",true,4100),
                "late ACK rejected");

        LocalPlanMachine cancel=fresh(99902);
        LocalPlanMachine.Event c=cancel.advance(null,0);
        ok(cancel.cancel(),"cancel accepted");
        ok(cancel.state()== LocalPlanMachine.State.CANCELLED,"cancel state");
        ok(!cancel.acknowledge(c.request.requestId,"",true,1),"late ACK after cancel");
        ok(cancel.advance(settings(),2000).request==null,"cancel prevents future dispatch");

        LocalPlanMachine rejected=fresh(99903);
        LocalPlanMachine.Event q=rejected.advance(null,0);
        ok(!rejected.acknowledge(q.request.requestId,"",false,2),"failed action ACK");
        ok("EFFECT_REJECTED_OR_UNCERTAIN".equals(rejected.reason()),"effect fail closed");

        LocalPlanMachine stale=fresh(99904);
        LocalPlanMachine.Event launch=stale.advance(null,0);
        ok(stale.acknowledge(launch.request.requestId,"",true,1),"setup");
        stale.advance(settings(),2);
        LocalPlanMachine.Event click=stale.advance(settings(),3);
        ok(!stale.acknowledge(click.request.requestId,"state-changed",true,4),
                "stale UI at dispatch blocked");
        ok("STALE_STATE_AT_DISPATCH".equals(stale.reason()),"stale reason");

        LocalPlanMachine mismatch=fresh(99905);
        LocalPlanMachine.Event first=mismatch.advance(null,0);
        mismatch.acknowledge(first.request.requestId,"",true,1);
        mismatch.advance(settings(),2);
        LocalPlanMachine.Observation wrongPkg=new LocalPlanMachine.Observation(
                "state-0001","com.other.app",
                Arrays.asList("Connections"),Collections.emptyList(),false);
        ok(mismatch.advance(wrongPkg,3).kind == LocalPlanMachine.EventKind.FINISHED,
                "selector package fail");
        ok(mismatch.reason().startsWith("SELECTOR_PACKAGE_MISMATCH"),"package reason");

        LocalPlanMachine missing=fresh(99906);
        LocalPlanMachine.Event req=missing.advance(null,0);
        missing.acknowledge(req.request.requestId,"",true,1);
        missing.advance(settings(),2);
        LocalPlanMachine.Observation duplicate=new LocalPlanMachine.Observation(
                "state-0001","com.android.settings",
                Arrays.asList("Settings","Connections"),
                Arrays.asList(new LocalSemanticSelector.Node(1,"com.android.settings",
                                "Connections",true,true),
                        new LocalSemanticSelector.Node(2,"com.android.settings",
                                "Connections",true,true)),false);
        ok(missing.advance(duplicate,3).kind == LocalPlanMachine.EventKind.FINISHED,
                "ambiguous selector fail");
        ok(missing.reason().contains("AMBIGUOUS"),"ambiguous reason");

        LocalPlanMachine noPkg=fresh(99907);
        LocalPlanMachine.Event init=noPkg.advance(null,0);
        noPkg.acknowledge(init.request.requestId,"",true,1);
        ok(noPkg.advance(null,4002).kind == LocalPlanMachine.EventKind.FINISHED,
                "package timeout");
        ok("STEP_TIMEOUT".equals(noPkg.reason()),"timeout reason");

        LocalPlanMachine reversed=fresh(99908);
        reversed.advance(null,10);
        ok(reversed.advance(null,9).kind == LocalPlanMachine.EventKind.FINISHED,
                "nonmonotonic clock fail");

        try {
            new LocalPlanMachine(LocalSkillRegistry.lookup("check battery usage"),"battery_012345");
            throw new AssertionError("unvalidated battery plan allowed");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }

    public static void main(String[] args) {
        for (int i=0;i<1000;i++) success(i);
        failures();
        System.out.println("LOCAL_PLAN_MACHINE_TESTS_PASS=" + checks);
        System.out.println("SIMULATED_SUCCESSFUL_MULTISTEP_TASKS=1000");
        System.out.println("SIDE_EFFECTS_PERFORMED=0");
    }
}
