@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.runtime

import java.util.ServiceLoader

internal val methodHandleRegistry: MethodHandleRegistry =
    ServiceLoader.load(MethodHandleRegistry::class.java).firstOrNull() ?: TransformedClassMethodHandleRegistry

internal val stacktraceElementRegistry: StacktraceElementRegistry = StacktraceElementRegistryImpl()

internal val enabled =
    System.getProperty("dev.reformator.stacktracedecoroutinator.enabled", "true").toBoolean()

internal val recoveryExplicitStacktrace =
    System.getProperty("dev.reformator.stacktracedecoroutinator.recoveryExplicitStacktrace", "true")
        .toBoolean()

internal const val ENABLED_PROPERTY = "dev.reformator.stacktracedecoroutinator.enabled"