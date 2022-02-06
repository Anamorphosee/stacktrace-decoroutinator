package dev.reformator.stacktracedecoroutinator.registry

interface DecoroutinatorRegistry {
    val stacktraceMethodHandleRegistry: DecoroutinatorStacktraceMethodHandleRegistry
    val continuationStacktraceElementRegistry: DecoroutinatorContinuationStacktraceElementRegistry
    val enabled: Boolean
    val recoveryExplicitStacktrace: Boolean
}

abstract class BaseDecoroutinatorRegistry: DecoroutinatorRegistry {
    final override val continuationStacktraceElementRegistry = DecoroutinatorContinuationStacktraceElementRegistryImpl()

    final override val enabled =
        System.getProperty("dev.reformator.stacktracedecoroutinator.enabled", "true").toBoolean()

    final override val recoveryExplicitStacktrace =
        System.getProperty("dev.reformator.stacktracedecoroutinator.recoveryExplicitStacktrace", "true")
            .toBoolean()
}

val decoroutinatorRegistry =
    Class.forName("dev.reformator.stacktracedecoroutinator.registry.DecoroutinatorRegistryImpl")
        .getField("INSTANCE")
        .get(null) as DecoroutinatorRegistry
