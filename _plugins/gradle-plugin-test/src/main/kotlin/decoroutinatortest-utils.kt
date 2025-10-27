@file:Suppress("PackageDirectoryMismatch")

package org.gradle.kotlin.dsl

import java.io.InputStream
import dev.reformator.stacktracedecoroutinator.classtransformer.internal.DebugMetadataInfo
import dev.reformator.stacktracedecoroutinator.gradleplugin.tryReadModuleInfo
import dev.reformator.stacktracedecoroutinator.gradleplugin.addRequiresModule
import dev.reformator.stacktracedecoroutinator.gradleplugin.classBody
import dev.reformator.stacktracedecoroutinator.provider.internal.internalName as providerInternalName

const val BASE_CONTINUATION_CLASS_NAME = dev.reformator.stacktracedecoroutinator.intrinsics.BASE_CONTINUATION_CLASS_NAME
const val PROVIDER_MODULE_NAME = dev.reformator.stacktracedecoroutinator.intrinsics.PROVIDER_MODULE_NAME

fun addReadProviderModuleToModuleInfo(input: InputStream): ByteArray? {
    val moduleNode = tryReadModuleInfo(input) ?: return null
    moduleNode.module.addRequiresModule(PROVIDER_MODULE_NAME)
    return moduleNode.classBody
}

fun transformClassBody(
    classBody: InputStream,
    metadataResolver: (className: String) -> DebugMetadataInfo?,
    skipSpecMethods: Boolean
) = dev.reformator.stacktracedecoroutinator.classtransformer.internal.transformClassBody(
    classBody = classBody,
    metadataResolver = metadataResolver,
    skipSpecMethods = skipSpecMethods
)

val String.internalName: String
    get() = providerInternalName
