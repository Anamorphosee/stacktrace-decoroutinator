@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.plugins

import dev.reformator.bytecodeprocessor.intrinsics.currentFileName
import dev.reformator.bytecodeprocessor.pluginapi.ProcessingDirectory
import dev.reformator.bytecodeprocessor.pluginapi.Processor
import dev.reformator.bytecodeprocessor.plugins.internal.eq
import dev.reformator.bytecodeprocessor.plugins.internal.readAsm
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode

object GetCurrentFileNameProcessor: Processor {
    override fun process(directory: ProcessingDirectory) {
        directory.classes.forEach { clazz ->
            clazz.node.methods?.forEach { method ->
                method.instructions?.forEach { instruction ->
                    if (instruction is MethodInsnNode && instruction eq currentFileNameInstruction) {
                        val fileName = clazz.node.sourceFile
                        val newInstruction = if (fileName == null) {
                            InsnNode(Opcodes.ACONST_NULL)
                        } else {
                            LdcInsnNode(fileName)
                        }
                        method.instructions.set(instruction, newInstruction)
                        clazz.markModified()
                    }
                }
            }
        }
    }

    private fun usage() {
        currentFileName
    }

    private val currentFileNameInstruction =
        GetCurrentFileNameProcessor::class.java.readAsm().methods.first { it.name == ::usage.name }.instructions
            .asSequence()
            .mapNotNull { it as? MethodInsnNode }
            .first()
}
