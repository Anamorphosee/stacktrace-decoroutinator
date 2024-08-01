package dev.reformator.stacktracedecoroutinator.jvmagent;

import dev.reformator.stacktracedecoroutinator.jvmagentcommon.*;

import java.lang.instrument.Instrumentation;

public class DecoroutinatorAgent {
    private DecoroutinatorAgent() { }

    public static void premain(String args, Instrumentation inst) {
        ClassTransformer_jvm_agent_commonKt.addDecoroutinatorTransformer(inst);
    }
}
