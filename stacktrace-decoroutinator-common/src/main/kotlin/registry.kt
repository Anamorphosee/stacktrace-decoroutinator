package dev.reformator.stacktracedecoroutinator.registry

internal val continuationStacktraceElementRegistryImpl = DecoroutinatorContinuationStacktraceElementRegistryImpl()

interface DecoroutinatorRegistry {
    val stacktraceMethodHandleRegistry: DecoroutinatorStacktraceMethodHandleRegistry

    val continuationStacktraceElementRegistry: DecoroutinatorContinuationStacktraceElementRegistry
    get() = continuationStacktraceElementRegistryImpl

    val enabled: Boolean
    get() = System.getProperty("dev.reformator.stacktracedecoroutinator.enabled", "true").toBoolean()
}

internal val decoroutinatorRegistry =
    Class.forName("dev.reformator.stacktracedecoroutinator.registry.DecoroutinatorRegistryImpl")
        .getField("INSTANCE")
        .get(null) as DecoroutinatorRegistry
