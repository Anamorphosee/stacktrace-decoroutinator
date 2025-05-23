@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal

import dev.reformator.stacktracedecoroutinator.generator.internal.DebugMetadataInfo
import dev.reformator.stacktracedecoroutinator.generator.internal.getDebugMetadataInfoFromClass
import dev.reformator.stacktracedecoroutinator.generator.internal.getDebugMetadataInfoFromClassBody
import dev.reformator.stacktracedecoroutinator.generator.internal.getResourceAsStream

interface JvmAgentCommonSettingsProvider {
    val metadataInfoResolveStrategy: MetadataInfoResolveStrategy
        get() = System.getProperty(
            "dev.reformator.stacktracedecoroutinator.jvmAgentDebugMetadataInfoResolveStrategy",
            MetadataInfoResolveStrategy.SYSTEM_RESOURCE_AND_CLASS.name
        ).let {
            MetadataInfoResolveStrategy.valueOf(it)
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

    companion object: JvmAgentCommonSettingsProvider
}

enum class MetadataInfoResolveStrategy: (String) -> DebugMetadataInfo? {
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
