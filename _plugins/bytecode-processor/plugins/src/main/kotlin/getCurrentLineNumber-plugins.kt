@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.plugins

import dev.reformator.bytecodeprocessor.intrinsics.currentLineNumber
import dev.reformator.bytecodeprocessor.pluginapi.ProcessingDirectory
import dev.reformator.bytecodeprocessor.pluginapi.Processor
import dev.reformator.bytecodeprocessor.plugins.internal.eq
import dev.reformator.bytecodeprocessor.plugins.internal.readAsm
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.MethodInsnNode

object GetCurrentLineNumberProcessor: Processor {
    override fun process(directory: ProcessingDirectory) {
        directory.classes.forEach { clazz ->
            clazz.node.methods?.forEach { method ->
                method.instructions?.forEach { instruction ->
                    if (instruction is MethodInsnNode && instruction eq currentLineNumberInstruction) {
                        var prevInstruction = instruction.previous
                        while (prevInstruction != null && prevInstruction !is LineNumberNode) {
                            prevInstruction = prevInstruction.previous
                        }
                        val lineNumber = if (prevInstruction is LineNumberNode) {
                            prevInstruction.line
                        } else {
                            -1
                        }
                        method.instructions.set(instruction, LdcInsnNode(lineNumber))
                        clazz.markModified()
                    }
                }
            }
        }
    }

    private fun usage() {
        currentLineNumber
    }

    private val currentLineNumberInstruction =
        GetCurrentLineNumberProcessor::class.java.readAsm().methods.first { it.name == ::usage.name }.instructions
            .asSequence()
            .mapNotNull { it as? MethodInsnNode }
            .first()
}