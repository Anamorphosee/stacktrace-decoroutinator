package dev.reformator.stacktracedecoroutinator.jvm

import dev.reformator.stacktracedecoroutinator.common.BaseDecoroutinatorRegistry
import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorStacktraceMethodHandleRegistry
import dev.reformator.stacktracedecoroutinator.jvmagentcommon.DecoroutinatorJvmAgentRegistryImpl
import java.lang.instrument.Instrumentation

object DecoroutinatorJvmRegistry: BaseDecoroutinatorRegistry() {
    override val stacktraceMethodHandleRegistry: DecoroutinatorStacktraceMethodHandleRegistry
        get() = DecorountinatorJvmStacktraceMethodHandleRegistry
}

class DecoroutinatorRuntimeJvmAgentRegistry(
    private val instrumentation: Instrumentation
): DecoroutinatorJvmAgentRegistryImpl() {
    private val _isBaseContinuationRetransformationAllowed: Boolean =
        System.getProperty(
            "dev.reformator.stacktracedecoroutinator.isBaseContinuationRetransformationAllowed",
            "true"
        ).toBoolean()

    private val _isRetransformationAllowed: Boolean =
        System.getProperty(
            "dev.reformator.stacktracedecoroutinator.isRetransformationAllowed",
            "false"
        ).toBoolean()

    override val isBaseContinuationRetransformationAllowed: Boolean
        get() = _isBaseContinuationRetransformationAllowed && instrumentation.isRetransformClassesSupported

    override val isRetransformationAllowed: Boolean
        get() = _isRetransformationAllowed && instrumentation.isRetransformClassesSupported

    override fun retransform(clazz: Class<*>) {
        instrumentation.retransformClasses(clazz)
    }
}
