package dev.reformator.stacktracedecoroutinator.jvmagent;

import dev.reformator.stacktracedecoroutinator.common.Registry_commonKt;
import dev.reformator.stacktracedecoroutinator.jvmagentcommon.*;

import java.lang.instrument.Instrumentation;

public class DecoroutinatorAgent {
    private DecoroutinatorAgent() { }

    public static void premain(String args, Instrumentation inst) {
        Utils_jvm_agent_commonKt.addDecoroutinatorClassFileTransformers(inst);
        Registry_commonKt.setDecoroutinatorRegistry(DecoroutinatorJvmRegistry.INSTANCE);
        Registry_jvm_agent_commonKt.setDecoroutinatorJvmAgentRegistry(new DecoroutinatorJvmAgentRegistryImpl(inst));
    }
}
