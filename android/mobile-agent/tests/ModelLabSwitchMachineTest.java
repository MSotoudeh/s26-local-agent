package io.projectir.mobile;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 1000 deterministic mock A/B switches; NEVER touches a phone or model weights. */
public final class ModelLabSwitchMachineTest {
    static int asserts;
    private static void check(boolean ok) {
        asserts++;
        if (!ok) throw new AssertionError("model-switch assertion #" + asserts);
    }
    private static class Mock implements ModelLabSwitchMachine.Adapter {
        final String id;
        final Log log;
        boolean health = true;
        boolean failLoad;
        boolean failUnload;
        Mock(String id, Log log) { this.id = id; this.log = log; }
        @Override public boolean load() {
            log.events += "L:" + id + ";";
            return !failLoad;
        }
        @Override public boolean health() {
            log.events += "H:" + id + ";";
            return health;
        }
        @Override public void unload() throws Exception {
            log.events += "U:" + id + ";";
            if (failUnload) throw new Exception("mock unload refusal");
        }
    }
    private static class Log { String events=""; }
    private static class Factory implements ModelLabSwitchMachine.Registry {
        final Map<String,Mock> models=new HashMap<>();
        final Set<String> allowed=new HashSet<>();
        @Override public boolean verifiedAndAllowed(String task,String id) {
            return "llm".equals(task) && allowed.contains(id);
        }
        @Override public ModelLabSwitchMachine.Adapter create(String task,String id) {
            return models.get(id);
        }
    }
    private static class Journal implements ModelLabSwitchMachine.Journal {
        final Map<String,String> active=new HashMap<>();
        boolean reject;
        @Override public String activeId(String task) { return active.getOrDefault(task,""); }
        @Override public boolean commit(String task,String id) {
            if(reject) return false;
            active.put(task,id);
            return true;
        }
    }
    private static void cycle() {
        Log log = new Log();
        Factory reg = new Factory();
        Journal journal = new Journal();
        Mock a = new Mock("qwen_baseline",log);
        Mock b = new Mock("qwen_candidate",log);
        reg.allowed.add(a.id); reg.allowed.add(b.id);
        reg.models.put(a.id,a);reg.models.put(b.id,b);
        ModelLabSwitchMachine machine = new ModelLabSwitchMachine(reg,journal);
        check(machine.activate("llm",a.id).outcome == ModelLabSwitchMachine.Outcome.ACTIVATED);
        check(journal.activeId("llm").equals(a.id));
        String initial=log.events;
        check(machine.activate("llm",a.id).outcome == ModelLabSwitchMachine.Outcome.ALREADY_ACTIVE);
        check(log.events.equals(initial));
        check(machine.activate("llm","never_verified").outcome == ModelLabSwitchMachine.Outcome.NOT_VERIFIED);
        check(machine.activate("llm","../private").outcome == ModelLabSwitchMachine.Outcome.INVALID);
        check(machine.activate("llm",b.id).outcome == ModelLabSwitchMachine.Outcome.ACTIVATED);
        check(journal.activeId("llm").equals(b.id));
        check(log.events.indexOf("U:qwen_baseline;") < log.events.lastIndexOf("L:qwen_candidate;"));
        b.health = false;
        check(machine.activate("llm",a.id).outcome == ModelLabSwitchMachine.Outcome.ACTIVATED);
        check(journal.activeId("llm").equals(a.id));
        check(machine.activate("llm",b.id).outcome == ModelLabSwitchMachine.Outcome.ROLLED_BACK);
        check(journal.activeId("llm").equals(a.id));
        check(machine.activate("llm",a.id).outcome == ModelLabSwitchMachine.Outcome.ALREADY_ACTIVE);
        b.health = true;
        journal.reject = true;
        check(machine.activate("llm",b.id).outcome == ModelLabSwitchMachine.Outcome.ROLLED_BACK);
        check(journal.activeId("llm").equals(a.id));
        journal.reject=false;
        b.failLoad=true;
        check(machine.activate("llm",b.id).outcome == ModelLabSwitchMachine.Outcome.ROLLED_BACK);
        check(journal.activeId("llm").equals(a.id));
        b.failLoad=false;
        a.failUnload=true;
        check(machine.activate("llm",b.id).outcome == ModelLabSwitchMachine.Outcome.ROLLBACK_FAILED);
        check(journal.activeId("llm").equals(a.id));
        a.failUnload=false;
        check(machine.activate("llm",b.id).outcome == ModelLabSwitchMachine.Outcome.ACTIVATED);
        check(journal.activeId("llm").equals(b.id));
        machine.unload("llm");
        check(!machine.busy());
        check(machine.activate("llm",a.id).outcome == ModelLabSwitchMachine.Outcome.ACTIVATED);
    }
    public static void main(String[] args) {
        for(int i=0;i<1000;i++) cycle();
        System.out.println("M1_MODEL_SWITCH_ASSERTIONS="+asserts);
        System.out.println("M1_MODEL_SWITCH_FIXTURE_ITERATIONS=1000");
        System.out.println("RUNTIME_DEVICE_EFFECTS=0");
    }
}
