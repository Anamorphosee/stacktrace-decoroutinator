@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.plugins

import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.pluginapi.ProcessingDirectory
import dev.reformator.bytecodeprocessor.pluginapi.Processor
import dev.reformator.bytecodeprocessor.plugins.internal.find
import dev.reformator.bytecodeprocessor.plugins.internal.internalName
import dev.reformator.bytecodeprocessor.plugins.internal.isStatic
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

class LoadConstantProcessor(valuesByMethod: Map<Key, Value>): Processor {
    data class Key(
        val ownerClassName: String,
        val methodName: String
    )

    data class Value(
        val value: Any,
        val deleteAfterModification: Boolean = true
    )

    override fun process(directory: ProcessingDirectory) {
        var modified = false
        directory.classes.forEach { clazz ->
            clazz.node.methods?.forEach { method ->
                method.instructions?.forEach { instruction ->
                    if (instruction is MethodInsnNode && instruction.opcode == Opcodes.INVOKESTATIC) {
                        val value = getValue(
                            ownerInternalName = instruction.owner,
                            methodName = instruction.name,
                            methodDescriptor = instruction.desc
                        )
                        if (value != null) {
                            method.instructions.set(instruction, LdcInsnNode(value.value))
                            clazz.markModified()
                            modified = true
                        }
                    }
                }
            }
        }

        if (!modified) {
            directory.classes.forEach { clazz ->
                val methodsToDelete = mutableListOf<MethodNode>()
                clazz.node.methods?.forEach { method ->
                    val loadConstantAnnotation = method.invisibleAnnotations.find(LoadConstant::class.java)
                    var loadsConstant = false
                    if (method.isStatic) {
                        val value = getValue(
                            ownerInternalName = clazz.node.name,
                            methodName = method.name,
                            methodDescriptor = method.desc
                        )
                        if (value != null) {
                            loadsConstant = true
                            if (value.deleteAfterModification) {
                                methodsToDelete.add(method)
                            }
                        }
                    }
                    if (loadConstantAnnotation != null && !loadsConstant) {
                        error("method [${method.name}] doesn't load constant")
                    }
                }
                if (methodsToDelete.isNotEmpty()) {
                    clazz.node.methods.removeAll(methodsToDelete)
                    clazz.markModified()
                }
            }
        }
    }

    private val valuesByInternalMethod = valuesByMethod.mapKeys { (key, _) ->
        key.copy(ownerClassName = key.ownerClassName.internalName)
    }

    private fun getValue(ownerInternalName: String, methodName: String, methodDescriptor: String): Value? {
        val methodType = Type.getMethodType(methodDescriptor)
        if (methodType.argumentCount == 0) {
            val value = valuesByInternalMethod[Key(
                ownerClassName = ownerInternalName,
                methodName = methodName
            )]
            if (value != null) {
                val valueClass = when (methodType.returnType) {
                    Type.INT_TYPE -> Int::class.javaObjectType
                    Type.LONG_TYPE -> Long::class.javaObjectType
                    Type.FLOAT_TYPE -> Float::class.javaObjectType
                    Type.DOUBLE_TYPE -> Double::class.javaObjectType
                    Type.getType(String::class.java) -> String::class.java
                    else -> null
                }
                if (valueClass == value.value.javaClass) {
                    return value
                }
            }
        }
        return null
    }
}
