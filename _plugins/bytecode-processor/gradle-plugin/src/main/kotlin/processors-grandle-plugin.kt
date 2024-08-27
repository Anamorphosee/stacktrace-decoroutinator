@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.gradleplugin

import dev.reformator.bytecodeprocessor.intrinsics.GET_GRADLE_PROJECT_VERSION_METHOD_NAME
import dev.reformator.bytecodeprocessor.intrinsics.getGradleProjectVersionClassName
import dev.reformator.bytecodeprocessor.pluginapi.ProcessingDirectory
import dev.reformator.bytecodeprocessor.pluginapi.Processor
import dev.reformator.bytecodeprocessor.plugins.internal.internalName
import org.gradle.api.Project
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode

class GetGradleProjectVersionProcessor(private val project: Project): Processor {
    override fun process(directory: ProcessingDirectory) {
        directory.classes.forEach { clazz ->
            clazz.node.methods?.forEach { method ->
                method.instructions?.forEach { instruction ->
                    if (instruction is MethodInsnNode && instruction.opcode == Opcodes.INVOKESTATIC
                        && instruction.owner == getGradleProjectVersionClassName.internalName
                        && instruction.name == GET_GRADLE_PROJECT_VERSION_METHOD_NAME
                        && instruction.desc == "()${Type.getDescriptor(String::class.java)}") {
                        method.instructions.set(instruction, LdcInsnNode(project.version.toString()))
                        clazz.markModified()
                    }
                }
            }
        }
    }
}
