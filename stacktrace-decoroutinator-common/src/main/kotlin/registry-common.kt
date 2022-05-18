package dev.reformator.stacktracedecoroutinator.common

interface DecoroutinatorRegistry {
    val stacktraceMethodHandleRegistry: DecoroutinatorStacktraceMethodHandleRegistry
    val continuationStacktraceElementRegistry: DecoroutinatorContinuationStacktraceElementRegistry
    val enabled: Boolean
    val recoveryExplicitStacktrace: Boolean
}

abstract class BaseDecoroutinatorRegistry: DecoroutinatorRegistry {
    override val continuationStacktraceElementRegistry =
        DecoroutinatorContinuationStacktraceElementRegistryImpl()

    override val enabled =
        System.getProperty("dev.reformator.stacktracedecoroutinator.enabled", "true").toBoolean()

    override val recoveryExplicitStacktrace =
        System
            .getProperty("dev.reformator.stacktracedecoroutinator.recoveryExplicitStacktrace", "true")
            .toBoolean()
}

var decoroutinatorRegistry: DecoroutinatorRegistry = object: DecoroutinatorRegistry {
    override val stacktraceMethodHandleRegistry: DecoroutinatorStacktraceMethodHandleRegistry
        get() = decoroutinatorRuntimeNotLoaded()
    override val continuationStacktraceElementRegistry: DecoroutinatorContinuationStacktraceElementRegistry
        get() = decoroutinatorRuntimeNotLoaded()
    override val enabled: Boolean
        get() = decoroutinatorRuntimeNotLoaded()
    override val recoveryExplicitStacktrace: Boolean
        get() = decoroutinatorRuntimeNotLoaded()

}

private fun decoroutinatorRuntimeNotLoaded(): Nothing =
    error("Decoroutinator registry is not set. Didn't you miss to call 'DecoroutinatorRuntime.load()'?")
