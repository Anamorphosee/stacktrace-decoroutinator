package dev.reformator.stacktracedecoroutinator.jvmagent;

import dev.reformator.stacktracedecoroutinator.common.BaseDecoroutinatorRegistry;
import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorStacktraceMethodHandleRegistry;
import dev.reformator.stacktracedecoroutinator.common.Registry_commonKt;
import dev.reformator.stacktracedecoroutinator.jvmagentcommon.DecoroutinatorJvmAgentStacktraceMethodHandleRegistry;
import dev.reformator.stacktracedecoroutinator.jvmagentcommon.Utils_jvm_agent_commonKt;

import java.lang.instrument.Instrumentation;

public class DecoroutinatorAgent {
    private DecoroutinatorAgent() { }

    public static void premain(String args, Instrumentation inst) throws ClassNotFoundException {
        Utils_jvm_agent_commonKt.addDecoroutinatorClassFileTransformers(inst);
        Registry_commonKt.setDecoroutinatorRegistry(new BaseDecoroutinatorRegistry() {
            @Override
            public DecoroutinatorStacktraceMethodHandleRegistry getStacktraceMethodHandleRegistry() {
                return DecoroutinatorJvmAgentStacktraceMethodHandleRegistry.INSTANCE;
            }
        });
    }
}
