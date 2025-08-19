@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.plugins

import dev.reformator.bytecodeprocessor.intrinsics.SkipInvocations
import dev.reformator.bytecodeprocessor.pluginapi.BytecodeProcessorContext
import dev.reformator.bytecodeprocessor.pluginapi.ProcessingDirectory
import dev.reformator.bytecodeprocessor.pluginapi.Processor
import dev.reformator.bytecodeprocessor.plugins.internal.find
import dev.reformator.bytecodeprocessor.plugins.internal.getParameter
import dev.reformator.bytecodeprocessor.plugins.internal.isStatic
import dev.reformator.bytecodeprocessor.plugins.internal.setParameter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

private const val APPLIED_PARAMETER = "applied"

object SkipInvocationsProcessor: Processor {
    data class Key(
        val classInternalName: String,
        val methodName: String,
        val methodDesc: String,
        val isStatic: Boolean
    ) {
        constructor(ownerInternalName: String, method: MethodNode): this(
            classInternalName = ownerInternalName,
            methodName = method.name,
            methodDesc = method.desc,
            isStatic = method.isStatic
        )
    }

    object ContextKey: BytecodeProcessorContext.Key<Set<Key>> {
        override val id: String
            get() = "skipInvocationKeys"
        override val default: Set<Key>
            get() = emptySet()
        override fun merge(value1: Set<Key>, value2: Set<Key>): Set<Key> =
            value1 + value2

    }

    override val usedContextKeys = listOf(ContextKey)

    override fun process(directory: ProcessingDirectory, context: BytecodeProcessorContext) {
        val values = directory.classes.flatMap { processingClass ->
            val methodsToDelete: MutableCollection<MethodNode> = mutableListOf()
            val list = processingClass.node.methods.orEmpty().mapNotNull { method ->
                val annotation = method.invisibleAnnotations.find(SkipInvocations::class.java)
                    ?: return@mapNotNull null

                val methodType = Type.getMethodType(method.desc)
                if (methodType.returnType == Type.VOID_TYPE) {
                    require(method.isStatic)
                    require(methodType.argumentCount == 0)
                } else if (method.isStatic) {
                    require(methodType.argumentCount == 1)
                    require(methodType.argumentTypes[0] == methodType.returnType)
                } else {
                    require(methodType.argumentCount == 0)
                    require(Type.getObjectType(processingClass.node.name) == methodType.returnType)
                }

                val deleteAfterChanging =
                    annotation.getParameter(SkipInvocations::deleteAfterChanging.name) as Boolean? ?: true

                if (annotation.getParameter(APPLIED_PARAMETER) as Boolean? ?: false) {
                    require(!deleteAfterChanging)
                    return@mapNotNull null
                }

                if (deleteAfterChanging) {
                    methodsToDelete.add(method)
                } else {
                    annotation.setParameter(APPLIED_PARAMETER, true)
                }
                processingClass.markModified()

                Key(processingClass.node.name, method)
            }
            if (methodsToDelete.isNotEmpty()) {
                require(processingClass.node.methods.removeAll(methodsToDelete))
            }
            list
        }.toSet()
        context.merge(ContextKey, values)

        directory.classes.forEach { processingClass ->
            processingClass.node.methods.orEmpty().forEach { method ->
                val iter = method.instructions?.iterator() ?: return@forEach
                while (iter.hasNext()) {
                    val instruction = iter.next()
                    if (instruction is MethodInsnNode) {
                        val key = Key(
                            classInternalName = instruction.owner,
                            methodName = instruction.name,
                            methodDesc = instruction.desc,
                            isStatic = instruction.opcode == Opcodes.INVOKESTATIC
                        )
                        if (key in context[ContextKey]) {
                            iter.remove()
                            processingClass.markModified()
                        }
                    }
                }
            }
        }
    }
}
