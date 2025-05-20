@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.plugins

import dev.reformator.bytecodeprocessor.intrinsics.GetOwnerClass
import dev.reformator.bytecodeprocessor.intrinsics.ownerClass
import dev.reformator.bytecodeprocessor.intrinsics.ownerMethodName
import dev.reformator.bytecodeprocessor.pluginapi.ProcessingDirectory
import dev.reformator.bytecodeprocessor.pluginapi.Processor
import dev.reformator.bytecodeprocessor.plugins.internal.eq
import dev.reformator.bytecodeprocessor.plugins.internal.find
import dev.reformator.bytecodeprocessor.plugins.internal.getParameter
import dev.reformator.bytecodeprocessor.plugins.internal.internalName
import dev.reformator.bytecodeprocessor.plugins.internal.isStatic
import dev.reformator.bytecodeprocessor.plugins.internal.readAsm
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

class GetOwnerClassProcessor(
    private val methods: Set<MethodKey> = emptySet()
): Processor {
    data class MethodKey(
        val className: String,
        val methodName: String
    )

    override fun process(directory: ProcessingDirectory) {
        val internalMethods = methods.asSequence()
            .map {
                MethodKey(
                    className = it.className.internalName,
                    methodName = it.methodName
                )
            }.toMutableSet()

        directory.classes.forEach { processingClass ->
            processingClass.node.methods?.forEach { method ->
                method.invisibleAnnotations.find(GetOwnerClass::class.java)?.let { _ ->
                    require(method.isStatic)
                    require(method.desc == "()${Type.getDescriptor(Class::class.java)}")
                    internalMethods.add(MethodKey(
                        className = processingClass.node.name,
                        methodName = method.name
                    ))
                }
            }
        }

        var modified = false
        directory.classes.forEach { processingClass ->
            processingClass.node.methods?.forEach { method ->
                method.instructions?.forEach { instruction ->
                    if (instruction is MethodInsnNode) {
                        if (instruction eq ownerMethodNameInstruction) {
                            method.instructions.set(
                                instruction,
                                LdcInsnNode(method.name)
                            )
                        } else if (instruction eq ownerClassInstruction) {
                            method.instructions.set(
                                instruction,
                                LdcInsnNode(Type.getObjectType(processingClass.node.name))
                            )
                        } else if (instruction.opcode == Opcodes.INVOKESTATIC
                            && instruction.desc == "()${Type.getDescriptor(Class::class.java)}") {
                            val key = MethodKey(
                                className = instruction.owner,
                                methodName = instruction.name
                            )
                            if (key in internalMethods) {
                                method.instructions.set(
                                    instruction,
                                    LdcInsnNode(Type.getObjectType(instruction.owner))
                                )
                                processingClass.markModified()
                                modified = true
                            }
                        }
                    }
                }
            }
        }

        if (!modified) {
            directory.classes.forEach { processingClass ->
                val methodsToDelete = mutableSetOf<MethodNode>()
                processingClass.node.methods?.forEach { method ->
                    method.invisibleAnnotations.find(GetOwnerClass::class.java)?.let { annotation ->
                        val deleteAfterModification =
                            annotation.getParameter(GetOwnerClass::deleteAfterModification.name) as Boolean?
                        if (deleteAfterModification == true) {
                            methodsToDelete.add(method)
                        }
                    }
                }
                if (methodsToDelete.isNotEmpty()) {
                    processingClass.markModified()
                    require(processingClass.node.methods.removeAll(methodsToDelete))
                }
            }
        }
    }

    private fun usageOwnerClass() {
        ownerClass
    }
    private fun usageOwnerMethodName() {
        ownerMethodName
    }

    companion object {
        private val ownerClassInstruction =
            GetOwnerClassProcessor::class.java.readAsm()
                .methods
                .first { it.name == GetOwnerClassProcessor::usageOwnerClass.name }
                .instructions
                .asSequence()
                .mapNotNull { it as? MethodInsnNode }
                .first()
        private val ownerMethodNameInstruction =
            GetOwnerClassProcessor::class.java.readAsm()
                .methods
                .first { it.name == GetOwnerClassProcessor::usageOwnerMethodName.name }
                .instructions
                .asSequence()
                .mapNotNull { it as? MethodInsnNode }
                .first()
    }
}