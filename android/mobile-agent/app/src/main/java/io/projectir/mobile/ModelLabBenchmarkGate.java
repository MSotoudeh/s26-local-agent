package io.projectir.mobile;

import java.util.concurrent.atomic.AtomicBoolean;

/** Shared single-operation lock for all in-app Model Lab ASR installs/benchmarks. */
public final class ModelLabBenchmarkGate {
    private static final AtomicBoolean GATE = new AtomicBoolean(false);
    private ModelLabBenchmarkGate() {}
    public static AtomicBoolean shared() { return GATE; }
}
