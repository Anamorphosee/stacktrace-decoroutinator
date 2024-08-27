@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.plugins

import dev.reformator.bytecodeprocessor.intrinsics.GET_CURRENT_LINE_NUMBER_METHOD_NAME
import dev.reformator.bytecodeprocessor.intrinsics.getCurrentLineNumberIntrinsicClassName
import dev.reformator.bytecodeprocessor.pluginapi.ProcessingDirectory
import dev.reformator.bytecodeprocessor.pluginapi.Processor
import dev.reformator.bytecodeprocessor.plugins.internal.internalName
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.MethodInsnNode

object GetCurrentLineNumberProcessor: Processor {
    override fun process(directory: ProcessingDirectory) {
        directory.classes.forEach { clazz ->
            clazz.node.methods?.forEach { method ->
                method.instructions?.forEach { instruction ->
                    if (instruction is MethodInsnNode && instruction.opcode == Opcodes.INVOKESTATIC
                        && instruction.owner == getCurrentLineNumberIntrinsicClassName.internalName
                        && instruction.name == GET_CURRENT_LINE_NUMBER_METHOD_NAME
                        && instruction.desc == "()${Type.INT_TYPE.descriptor}"
                    ) {
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
}