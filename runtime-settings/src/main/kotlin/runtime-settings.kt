@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.runtimesettings

interface DecoroutinatorRuntimeSettingsProvider {
    //Common settings

    val enabled: Boolean
        get() = System.getProperty("dev.reformator.stacktracedecoroutinator.enabled", "true").toBoolean()

    val recoveryExplicitStacktrace: Boolean
        get() = System.getProperty(
            "dev.reformator.stacktracedecoroutinator.recoveryExplicitStacktrace",
            "true"
        ).toBoolean()

    val recoveryExplicitStacktraceTimeoutMs: UInt
        get() = System.getProperty(
            "dev.reformator.stacktracedecoroutinator.recoveryExplicitStacktraceTimeoutMs",
            "500"
        ).toUInt()

    val tailCallDeoptimize: Boolean
        get() = System.getProperty("dev.reformator.stacktracedecoroutinator.tailCallDeoptimize", "true")
            .toBoolean()

    val methodsNumberThreshold: Int
        get() = System.getProperty("dev.reformator.stacktracedecoroutinator.methodsNumberThreshold", "50")
            .toInt()

    val restoreCoroutineStackFrames: Boolean
        get() = System.getProperty(
            "dev.reformator.stacktracedecoroutinator.restoreCoroutineStackFrames",
            "true"
        ).toBoolean()

    // JVM Agent settings

    val metadataInfoResolveStrategy: DecoroutinatorMetadataInfoResolveStrategy
        get() = System.getProperty(
            "dev.reformator.stacktracedecoroutinator.metadataInfoResolveStrategy",
            DecoroutinatorMetadataInfoResolveStrategy.SYSTEM_RESOURCE_AND_CLASS.name
        ).let {
            DecoroutinatorMetadataInfoResolveStrategy.valueOf(it)
        }

    val isBaseContinuationRedefinitionAllowed: Boolean
        get() = System.getProperty(
            "dev.reformator.stacktracedecoroutinator.isBaseContinuationRedefinitionAllowed",
            "true"
        ).toBoolean()

    val isRedefinitionAllowed: Boolean
        get() = System.getProperty(
            "dev.reformator.stacktracedecoroutinator.isRedefinitionAllowed",
            "false"
        ).toBoolean()

    // Embedded Debug Probes settings

    val enableCreationStackTraces: Boolean
        get() = System.getProperty(
            "dev.reformator.stacktracedecoroutinator.enableCreationStackTraces",
            "false"
        ).toBoolean()

    val installDebugProbes: Boolean
        get() = System.getProperty(
            "dev.reformator.stacktracedecoroutinator.installDebugProbes",
            "true"
        ).toBoolean()

    // end

    val priority: Int
        get() = 0

    companion object: DecoroutinatorRuntimeSettingsProvider
}

enum class DecoroutinatorMetadataInfoResolveStrategy {
    SYSTEM_RESOURCE, CLASS, SYSTEM_RESOURCE_AND_CLASS
}
