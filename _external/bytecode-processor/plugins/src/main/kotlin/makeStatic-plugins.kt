@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.plugins

import dev.reformator.bytecodeprocessor.intrinsics.MakeStatic
import dev.reformator.bytecodeprocessor.pluginapi.ProcessingDirectory
import dev.reformator.bytecodeprocessor.pluginapi.Processor
import dev.reformator.bytecodeprocessor.plugins.internal.find
import dev.reformator.bytecodeprocessor.plugins.internal.forEachAllowingAddingToEnd
import dev.reformator.bytecodeprocessor.plugins.internal.getOrCreateClinit
import dev.reformator.bytecodeprocessor.plugins.internal.getParameter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode

object MakeStaticProcessor: Processor {
    override fun process(directory: ProcessingDirectory) {
        modify(directory)
    }

    private data class Key(
        val ownerInternalName: String,
        val name: String,
        val descriptor: String
    )

    private fun modify(module: ProcessingDirectory) {
        val newDescriptorsByMethod = module.classes.asSequence().flatMap { processingClass ->
            sequence {
                processingClass.node.methods?.forEachAllowingAddingToEnd { method ->
                    val annotation = method.invisibleAnnotations.find(MakeStatic::class.java) ?:
                        return@forEachAllowingAddingToEnd

                    if (annotation.getParameter(APPLIED_PARAMETER) as Boolean? != true) {
                        require(method.access and Opcodes.ACC_STATIC == 0)
                        processingClass.markModified()

                        annotation.values.add(APPLIED_PARAMETER)
                        annotation.values.add(true)
                        method.access = method.access or Opcodes.ACC_STATIC
                        val descriptor = Type.getMethodType(method.desc)
                        val newDescriptor = Type.getMethodType(
                            descriptor.returnType,
                            *Array<Type>(descriptor.argumentTypes.size + 1) {
                                when (it) {
                                    0 -> Type.getObjectType(processingClass.node.name)
                                    else -> descriptor.argumentTypes[it - 1]
                                }
                            }
                        )
                        method.desc = newDescriptor.descriptor

                        if (annotation.getParameter(MakeStatic::addToStaticInitializer.name) as Boolean? == true) {
                            require(descriptor.argumentTypes.isEmpty())
                            require(descriptor.returnType == Type.VOID_TYPE)

                            val clinit = processingClass.node.getOrCreateClinit()

                            clinit.instructions.insertBefore(
                                clinit.instructions.first,
                                InsnList().apply {
                                    add(InsnNode(Opcodes.ACONST_NULL))
                                    add(
                                        MethodInsnNode(
                                            Opcodes.INVOKESTATIC,
                                            processingClass.node.name,
                                            method.name,
                                            method.desc
                                        )
                                    )
                                }
                            )
                        }
                    }

                    require(method.access and Opcodes.ACC_STATIC != 0)

                    val descriptor = Type.getMethodType(method.desc)
                    require(descriptor.argumentTypes.isNotEmpty())
                    require(descriptor.argumentTypes[0] == Type.getObjectType(processingClass.node.name))

                    val oldDescriptor = Type.getMethodType(
                        descriptor.returnType,
                        *Array<Type>(descriptor.argumentCount - 1) {
                            descriptor.argumentTypes[it + 1]
                        }
                    )

                    val key = Key(
                        ownerInternalName = processingClass.node.name,
                        name = method.name,
                        descriptor = oldDescriptor.descriptor
                    )

                    yield(key to method.desc)
                }
            }
        }.toMap()

        module.classes.forEach { processingClass ->
            processingClass.node.methods?.forEach method@{ method ->
                method.instructions?.forEach { instruction ->
                    if (instruction is MethodInsnNode && instruction.opcode != Opcodes.INVOKESTATIC) {
                        val key = Key(
                            ownerInternalName = instruction.owner,
                            name = instruction.name,
                            descriptor = instruction.desc
                        )
                        val newDescriptor = newDescriptorsByMethod[key]
                        if (newDescriptor != null) {
                            processingClass.markModified()
                            instruction.opcode = Opcodes.INVOKESTATIC
                            instruction.desc = newDescriptor
                        }
                    }
                }
            }
        }
    }
}

private const val APPLIED_PARAMETER = "applied"
