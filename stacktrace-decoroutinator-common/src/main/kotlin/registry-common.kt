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

lateinit var decoroutinatorRegistry: DecoroutinatorRegistry
