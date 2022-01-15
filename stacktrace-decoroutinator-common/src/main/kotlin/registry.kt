package dev.reformator.stacktracedecoroutinator.registry

internal val continuationStacktraceElementRegistryImpl = DecoroutinatorContinuationStacktraceElementRegistryImpl()

const val DECOROUTINATOR_ENABLED_PROPERTY = "dev.reformator.stacktracedecoroutinator.enabled"
const val DECOROUTINATOR_RECOVERY_EXPLICIT_STACKTRACE_PROPERTY =
    "dev.reformator.stacktracedecoroutinator.recoveryExplicitStacktrace"

interface DecoroutinatorRegistry {
    val stacktraceMethodHandleRegistry: DecoroutinatorStacktraceMethodHandleRegistry

    val continuationStacktraceElementRegistry: DecoroutinatorContinuationStacktraceElementRegistry
    get() = continuationStacktraceElementRegistryImpl

    val enabled: Boolean
    get() = System.getProperty(DECOROUTINATOR_ENABLED_PROPERTY, "true").toBoolean()

    val recoveryExplicitStacktrace: Boolean
    get() = System.getProperty(DECOROUTINATOR_RECOVERY_EXPLICIT_STACKTRACE_PROPERTY, "true").toBoolean()
}

val decoroutinatorRegistry =
    Class.forName("dev.reformator.stacktracedecoroutinator.registry.DecoroutinatorRegistryImpl")
        .getField("INSTANCE")
        .get(null) as DecoroutinatorRegistry
