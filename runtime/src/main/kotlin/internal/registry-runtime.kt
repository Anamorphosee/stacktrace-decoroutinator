@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.runtime.internal

import java.util.ServiceLoader

internal const val ENABLED_PROPERTY = "dev.reformator.stacktracedecoroutinator.enabled"

internal val specRegistry: SpecRegistry =
    ServiceLoader.load(SpecRegistry::class.java).firstOrNull() ?: TransformedClassesSpecRegistry

internal val stacktraceElementRegistry: StacktraceElementRegistry = StacktraceElementRegistryImpl()

internal val enabled =
    System.getProperty(ENABLED_PROPERTY, "true").toBoolean()

internal val recoveryExplicitStacktrace =
    System.getProperty("dev.reformator.stacktracedecoroutinator.recoveryExplicitStacktrace", "true")
        .toBoolean()
