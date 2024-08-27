@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.plugins

import dev.reformator.bytecodeprocessor.intrinsics.GET_CURRENT_FILE_NAME_METHOD_NAME
import dev.reformator.bytecodeprocessor.intrinsics.getCurrentFileNameIntrinsicClassName
import dev.reformator.bytecodeprocessor.pluginapi.ProcessingDirectory
import dev.reformator.bytecodeprocessor.pluginapi.Processor
import dev.reformator.bytecodeprocessor.plugins.internal.internalName
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode

object GetCurrentFileNameProcessor: Processor {
    override fun process(directory: ProcessingDirectory) {
        directory.classes.forEach { clazz ->
            clazz.node.methods?.forEach { method ->
                method.instructions?.forEach { instruction ->
                    if (instruction is MethodInsnNode && instruction.opcode == Opcodes.INVOKESTATIC
                        && instruction.owner == getCurrentFileNameIntrinsicClassName.internalName
                        && instruction.name == GET_CURRENT_FILE_NAME_METHOD_NAME
                        && instruction.desc == "()${Type.getDescriptor(String::class.java)}"
                    ) {
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
}
