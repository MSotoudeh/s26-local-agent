package io.projectir.mobile;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Pure host race test: no concurrent native model install/inference allowed. */
public final class ModelLabBenchmarkGateTest {
    public static void main(String[] args) throws Exception {
        AtomicBoolean gate=ModelLabBenchmarkGate.shared();
        if(gate.get()) throw new AssertionError("GATE_NOT_IDLE");
        for(int cycle=0; cycle<100; cycle++){
            final int n=16;
            CountDownLatch start=new CountDownLatch(1);
            CountDownLatch finished=new CountDownLatch(n);
            AtomicInteger accepted=new AtomicInteger();
            for(int i=0;i<n;i++){
                new Thread(()->{
                    try{
                        start.await();
                        if(gate.compareAndSet(false,true)) accepted.incrementAndGet();
                    }catch(InterruptedException ex){Thread.currentThread().interrupt();}
                    finally { finished.countDown(); }
                }, "gate-racer").start();
            }
            start.countDown();
            finished.await();
            if(accepted.get()!=1) throw new AssertionError("UNSAFE_PARALLEL_NATIVE_RUNS_"+accepted.get());
            gate.set(false);
        }
        System.out.println("M2_MODEL_GATE_100x16_RACE_PASS");
    }
}
