@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.jvmagentcommon

import dev.reformator.stacktracedecoroutinator.generator.internal.DebugMetadataInfo
import dev.reformator.stacktracedecoroutinator.generator.internal.getDebugMetadataInfoFromClass
import dev.reformator.stacktracedecoroutinator.generator.internal.getDebugMetadataInfoFromClassBody
import dev.reformator.stacktracedecoroutinator.generator.internal.getResourceAsStream

internal enum class JvmAgentDebugMetadataInfoResolveStrategy: (String) -> DebugMetadataInfo? {
    SYSTEM_RESOURCE {
        override fun invoke(className: String): DebugMetadataInfo? {
            val path = className.replace('.', '/') + ".class"
            return getResourceAsStream(path)?.let { resource ->
                resource.use {
                    getDebugMetadataInfoFromClassBody(resource)
                }
            }
        }
    },

    CLASS {
        override fun invoke(className: String): DebugMetadataInfo? {
            val clazz = try { Class.forName(className) } catch (_: ClassNotFoundException) { null }
            return clazz?.let { getDebugMetadataInfoFromClass(it) }
        }
    },

    SYSTEM_RESOURCE_AND_CLASS {
        override fun invoke(className: String): DebugMetadataInfo? =
            SYSTEM_RESOURCE(className) ?: CLASS(className)
    }
}

internal val isBaseContinuationTransformationAllowed = System.getProperty(
        "dev.reformator.stacktracedecoroutinator.isBaseContinuationTransformationAllowed",
        "true"
    ).toBoolean()

internal val isTransformationAllowed = System.getProperty(
        "dev.reformator.stacktracedecoroutinator.isTransformationAllowed",
        "true"
    ).toBoolean()

internal val isBaseContinuationRetransformationAllowed = System.getProperty(
        "dev.reformator.stacktracedecoroutinator.isBaseContinuationRetransformationAllowed",
        "true"
    ).toBoolean()

internal val isRetransformationAllowed = System.getProperty(
        "dev.reformator.stacktracedecoroutinator.isRetransformationAllowed",
        "false"
    ).toBoolean()

internal val jvmAgentDebugMetadataInfoResolveStrategy = System.getProperty(
        "dev.reformator.stacktracedecoroutinator.jvmAgentDebugMetadataInfoResolveStrategy",
        JvmAgentDebugMetadataInfoResolveStrategy.SYSTEM_RESOURCE_AND_CLASS.name
    ).let {
        JvmAgentDebugMetadataInfoResolveStrategy.valueOf(it)
    }
