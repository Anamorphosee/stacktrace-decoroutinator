@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.gradleplugin

import dev.reformator.bytecodeprocessor.impl.applyBytecodeProcessors
import dev.reformator.bytecodeprocessor.pluginapi.Processor
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.kotlin.dsl.bytecodeProcessor
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

open class BytecodeProcessorPluginExtension {
    var processors = setOf<Processor>()
}

class BytecodeProcessorPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val extension = extensions.create(::bytecodeProcessor.name, BytecodeProcessorPluginExtension::class.java)
            afterEvaluate { _ ->
                tasks.withType(AbstractCompile::class.java) { task ->
                    task.doLast { _ ->
                        val processors = extension.processors
                        if (processors.isNotEmpty()) {
                            applyBytecodeProcessors(processors, task.destinationDirectory.get().asFile)
                        }
                    }
                }
                tasks.withType(KotlinJvmCompile::class.java) { task ->
                    task.doLast { _ ->
                        val processors = extension.processors
                        if (processors.isNotEmpty()) {
                            applyBytecodeProcessors(processors, task.destinationDirectory.get().asFile)
                        }
                    }
                }
            }
        }
    }
}
