@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.runtime

import java.util.ServiceLoader

interface DecoroutinatorRegistry {
    val methodHandleRegistry: MethodHandleRegistry
    val stacktraceElementRegistry: StacktraceElementRegistry
    val enabled: Boolean
    val recoveryExplicitStacktrace: Boolean
}

open class BaseDecoroutinatorRegistry: DecoroutinatorRegistry {
    override val methodHandleRegistry: MethodHandleRegistry
        get() = TransformedClassMethodHandleRegistry

    override val stacktraceElementRegistry =
        StacktraceElementRegistryImpl()

    override val enabled =
        System.getProperty("dev.reformator.stacktracedecoroutinator.enabled", "true").toBoolean()

    override val recoveryExplicitStacktrace =
        System
            .getProperty("dev.reformator.stacktracedecoroutinator.recoveryExplicitStacktrace", "true")
            .toBoolean()
}

val decoroutinatorRegistry: DecoroutinatorRegistry =
    ServiceLoader.load(DecoroutinatorRegistry::class.java).firstOrNull() ?: BaseDecoroutinatorRegistry()
