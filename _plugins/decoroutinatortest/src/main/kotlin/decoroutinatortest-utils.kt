@file:Suppress("PackageDirectoryMismatch")

package org.gradle.kotlin.dsl

import java.io.InputStream
import dev.reformator.stacktracedecoroutinator.gradleplugin.tryReadModuleInfo
import dev.reformator.stacktracedecoroutinator.gradleplugin.addRequiresModule
import dev.reformator.stacktracedecoroutinator.generator.internal.PROVIDER_MODULE_NAME
import dev.reformator.stacktracedecoroutinator.gradleplugin.classBody

fun addReadProviderModuleToModuleInfo(input: InputStream): ByteArray? {
    val moduleNode = tryReadModuleInfo(input) ?: return null
    moduleNode.module.addRequiresModule(PROVIDER_MODULE_NAME)
    return moduleNode.classBody
}
