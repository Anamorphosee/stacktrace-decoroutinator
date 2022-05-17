package dev.reformator.stacktracedecoroutinator.jvmagent;

import dev.reformator.stacktracedecoroutinator.common.BaseDecoroutinatorRegistry;
import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorStacktraceMethodHandleRegistry;
import dev.reformator.stacktracedecoroutinator.common.Registry_commonKt;
import dev.reformator.stacktracedecoroutinator.jvmagentcommon.DecoroutinatorClassFileTransformer;
import dev.reformator.stacktracedecoroutinator.jvmagentcommon.DecoroutinatorJvmAgentStacktraceMethodHandleRegistry;
import kotlin.coroutines.jvm.internal.BaseContinuationImpl;

import java.lang.instrument.Instrumentation;

public class DecoroutinatorAgent {
    public static void premain(String args, Instrumentation inst) {
        inst.addTransformer(DecoroutinatorClassFileTransformer.INSTANCE);
        BaseContinuationImpl.class.getName();
        Registry_commonKt.setDecoroutinatorRegistry(new BaseDecoroutinatorRegistry() {
            @Override
            public DecoroutinatorStacktraceMethodHandleRegistry getStacktraceMethodHandleRegistry() {
                return DecoroutinatorJvmAgentStacktraceMethodHandleRegistry.INSTANCE;
            }
        });
    }
}
