@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.runtime

import java.util.ServiceLoader

internal val methodHandleRegistry: MethodHandleRegistry =
    ServiceLoader.load(MethodHandleRegistry::class.java).firstOrNull() ?: TransformedClassMethodHandleRegistry

internal val stacktraceElementRegistry: StacktraceElementRegistry =
    ServiceLoader.load(StacktraceElementRegistry::class.java).firstOrNull() ?: StacktraceElementRegistryImpl()

internal val enabled =
    System.getProperty("dev.reformator.stacktracedecoroutinator.enabled", "true").toBoolean()

internal val recoveryExplicitStacktrace =
    System.getProperty("dev.reformator.stacktracedecoroutinator.recoveryExplicitStacktrace", "true")
        .toBoolean()
