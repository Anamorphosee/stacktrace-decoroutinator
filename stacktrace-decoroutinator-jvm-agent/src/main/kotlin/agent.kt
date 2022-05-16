package dev.reformator.stacktracedecoroutinator.agent

import dev.reformator.stacktracedecoroutinator.common.BaseDecoroutinatorRegistry
import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorStacktraceMethodHandleRegistry
import dev.reformator.stacktracedecoroutinator.common.decoroutinatorRegistry
import dev.reformator.stacktracedecoroutinator.jvmagentcommon.DecoroutinatorClassFileTransformer
import dev.reformator.stacktracedecoroutinator.jvmagentcommon.DecoroutinatorJvmAgentStacktraceMethodHandleRegistry
import java.lang.instrument.Instrumentation


fun premain(args: String?, inst: Instrumentation) {
    inst.addTransformer(DecoroutinatorClassFileTransformer)
    decoroutinatorRegistry = object: BaseDecoroutinatorRegistry() {
        override val stacktraceMethodHandleRegistry: DecoroutinatorStacktraceMethodHandleRegistry
            get() = DecoroutinatorJvmAgentStacktraceMethodHandleRegistry
    }
}
