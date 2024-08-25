@file:Suppress("PackageDirectoryMismatch")

package org.gradle.kotlin.dsl

import dev.reformator.bytecodeprocessor.gradleplugin.BytecodeProcessorPluginExtension
import org.gradle.api.Project

fun Project.bytecodeProcessor(configure: BytecodeProcessorPluginExtension.() -> Unit) {
    extensions.configure(::bytecodeProcessor.name, configure)
}
