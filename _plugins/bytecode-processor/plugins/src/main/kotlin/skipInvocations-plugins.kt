@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.plugins

import dev.reformator.bytecodeprocessor.intrinsics.SkipInvocations
import dev.reformator.bytecodeprocessor.pluginapi.ProcessingDirectory
import dev.reformator.bytecodeprocessor.pluginapi.Processor
import dev.reformator.bytecodeprocessor.plugins.internal.find
import dev.reformator.bytecodeprocessor.plugins.internal.getParameter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

object SkipInvocationsProcessor: Processor {
    override fun process(directory: ProcessingDirectory) {
        if (modify(directory)) return
        deleteAfterModification(directory)
    }

    private data class Key(
        val ownerInternalName: String,
        val name: String,
        val descriptor: String
    ) {
        constructor(ownerInternalName: String, method: MethodNode): this(
            ownerInternalName = ownerInternalName,
            name = method.name,
            descriptor = method.desc
        )
    }

    private fun modify(module: ProcessingDirectory): Boolean {
        val skipInvocationMethods = module.classes.asSequence().flatMap { processingClass ->
            processingClass.node.methods.orEmpty().asSequence().mapNotNull { method ->
                if (method.invisibleAnnotations.find(SkipInvocations::class.java) == null) return@mapNotNull null

                val methodType = Type.getMethodType(method.desc)
                if (methodType.returnType == Type.VOID_TYPE) {
                    require(method.access and Opcodes.ACC_STATIC != 0)
                    require(methodType.argumentTypes.isEmpty())
                } else if (method.access and Opcodes.ACC_STATIC != 0) {
                    require(methodType.argumentTypes.size == 1)
                    require(methodType.argumentTypes[0] == methodType.returnType)
                } else {
                    require(methodType.argumentTypes.isEmpty())
                    require(Type.getObjectType(processingClass.node.name) == methodType.returnType)
                }

                Key(
                    ownerInternalName = processingClass.node.name,
                    method = method
                )
            }
        }.toSet()

        var modified = false

        module.classes.forEach { processingClass ->
            processingClass.node.methods?.forEach method@{ method ->
                val iter = method.instructions?.iterator() ?: return@method
                while (iter.hasNext()) {
                    val instruction = iter.next()
                    if (instruction is MethodInsnNode) {
                        val key = Key(
                            ownerInternalName = instruction.owner,
                            name = instruction.name,
                            descriptor = instruction.desc
                        )
                        if (key in skipInvocationMethods) {
                            iter.remove()
                            modified = true
                            processingClass.markModified()
                        }
                    }
                }
            }
        }

        return modified
    }
}

private fun deleteAfterModification(module: ProcessingDirectory): Boolean {
    var modified = false
    module.classes.forEach { processingClass ->
        val iter = processingClass.node.methods?.iterator() ?: return@forEach
        while (iter.hasNext()) {
            val method = iter.next()
            val annotation = method.invisibleAnnotations.find(SkipInvocations::class.java) ?: continue
            if (annotation.getParameter(SkipInvocations::deleteAfterChanging.name) as Boolean? == false) continue
            iter.remove()
            processingClass.markModified()
            modified = true
        }
    }
    return modified
}
