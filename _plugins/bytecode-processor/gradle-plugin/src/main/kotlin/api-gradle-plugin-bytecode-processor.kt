@file:Suppress("PackageDirectoryMismatch")

package org.gradle.kotlin.dsl

import dev.reformator.bytecodeprocessor.gradleplugin.BytecodeProcessorPluginExtension
import dev.reformator.bytecodeprocessor.gradleplugin.EXTENSION_NAME
import dev.reformator.bytecodeprocessor.gradleplugin.INIT_TASK_NAME
import org.gradle.api.Project
import org.gradle.api.Task

fun Project.bytecodeProcessor(configure: BytecodeProcessorPluginExtension.() -> Unit) {
    extensions.configure(EXTENSION_NAME, configure)
}

val Project.bytecodeProcessor: BytecodeProcessorPluginExtension
    get() = extensions.getByName(EXTENSION_NAME) as BytecodeProcessorPluginExtension

val Project.bytecodeProcessorInitTask: Task
    get() = tasks.findByName(INIT_TASK_NAME)!!
