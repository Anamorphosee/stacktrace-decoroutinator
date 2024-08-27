@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.plugins

import dev.reformator.bytecodeprocessor.intrinsics.ChangeClassName
import dev.reformator.bytecodeprocessor.pluginapi.ProcessingDirectory
import dev.reformator.bytecodeprocessor.pluginapi.Processor
import dev.reformator.bytecodeprocessor.plugins.internal.find
import dev.reformator.bytecodeprocessor.plugins.internal.getParameter
import dev.reformator.bytecodeprocessor.plugins.internal.internalName
import dev.reformator.bytecodeprocessor.plugins.internal.setParameter
import org.objectweb.asm.Handle
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*

class ChangeClassNameProcessor(
    private val replacesClassNamesByOriginalName: Map<String, String> = emptyMap()
): Processor {
    override fun process(directory: ProcessingDirectory) {
        if (modify(directory)) return
        deleteAfterModification(directory)
    }

    private fun modify(module: ProcessingDirectory): Boolean {
        var modified = false

        val replacedClassInternalNamesByOriginalInternalName = mutableMapOf<String, String>()
        replacesClassNamesByOriginalName.forEach { (originalName, replacedName) ->
           replacedClassInternalNamesByOriginalInternalName[originalName.internalName] = replacedName.internalName
        }
        module.classes.forEach { processingClass ->
            val annotation = processingClass.node.invisibleAnnotations.find(ChangeClassName::class.java) ?: return@forEach

            val toParameter = annotation.getParameter(ChangeClassName::to.name) as Type?
            val toNameParameter = annotation.getParameter(ChangeClassName::toName.name) as String?
            if (toParameter != null && toNameParameter != null) {
                error("both parameters [${ChangeClassName::to.name}] and [${ChangeClassName::toName.name}] were set")
            }

            val toInternalName = when {
                toParameter != null -> toParameter.internalName
                toNameParameter != null -> toNameParameter.internalName
                else -> error("both parameters [${ChangeClassName::to.name}] and [${ChangeClassName::toName.name}] weren't set")
            }

            val fromInternalNameParameter = annotation.getParameter(FROM_INTERNAL_NAME) as String?
            val fromInternalName = if (fromInternalNameParameter == null) {
                require(processingClass.node.name != toInternalName)
                val oldInternalName = processingClass.node.name
                modified = true
                processingClass.markModified()
                annotation.setParameter(FROM_INTERNAL_NAME, oldInternalName)
                processingClass.node.name = toInternalName
                oldInternalName
            } else {
                require(processingClass.node.name == toInternalName)
                fromInternalNameParameter
            }

            replacedClassInternalNamesByOriginalInternalName[fromInternalName] = toInternalName
        }

        fun String?.modify(onChange: (newValue: String) -> Unit) {
            var value = this ?: return
            replacedClassInternalNamesByOriginalInternalName.forEach { (internalName, replacedClassInternalName) ->
                value = value.replace(internalName, replacedClassInternalName)
            }
            if (this != value) {
                modified = true
                onChange(value)
            }
        }

        fun MutableList<Any>?.modifyFrames(onModified: () -> Unit) {
            if (this == null) return
            var localModified = false
            forEachIndexed { index, value ->
                if (value is String) {
                    value.modify {
                        this[index] = it
                        localModified = true
                    }
                }
            }
            if (localModified) {
                onModified()
            }
        }

        fun Handle.modify(onModified: (Handle) -> Unit) {
            var localModified = false
            var owner = owner
            owner.modify {
                owner = it
                localModified = true
            }
            var desc = desc
            desc.modify {
                desc = it
                localModified = true
            }
            if (localModified) {
                onModified(Handle(
                    tag,
                    owner,
                    name,
                    desc,
                    isInterface
                ))
            }
        }

        module.classes.forEach { processingClass ->
            processingClass.node.superName?.modify {
                processingClass.markModified()
                processingClass.node.superName = it
            }
            processingClass.node.interfaces?.forEachIndexed { index, superInterface ->
                superInterface.modify {
                    processingClass.markModified()
                    processingClass.node.interfaces[index] = it
                }
            }
            processingClass.node.signature.modify {
                processingClass.markModified()
                processingClass.node.signature = it
            }

            processingClass.node.fields?.forEach { field ->
                field.desc.modify {
                    processingClass.markModified()
                    field.desc = it
                }
                field.signature.modify {
                    processingClass.markModified()
                    field.signature = it
                }
            }

            processingClass.node.methods?.forEach { method ->
                method.desc.modify {
                    processingClass.markModified()
                    method.desc = it
                }
                method.signature.modify {
                    processingClass.markModified()
                    method.signature = it
                }
                method.localVariables.orEmpty().forEach { variable ->
                    variable.desc.modify {
                        processingClass.markModified()
                        variable.desc = it
                    }
                    variable.signature.modify {
                        processingClass.markModified()
                        variable.signature = it
                    }
                }
                method.instructions?.forEach { instruction ->
                    when (instruction) {
                        is MethodInsnNode -> {
                            instruction.owner.modify {
                                processingClass.markModified()
                                instruction.owner = it
                            }
                            instruction.desc.modify {
                                processingClass.markModified()
                                instruction.desc = it
                            }
                        }
                        is FieldInsnNode -> {
                            instruction.owner.modify {
                                processingClass.markModified()
                                instruction.owner = it
                            }
                            instruction.desc.modify {
                                processingClass.markModified()
                                instruction.desc = it
                            }
                        }
                        is FrameNode -> {
                            instruction.local.modifyFrames { processingClass.markModified() }
                            instruction.stack.modifyFrames { processingClass.markModified() }
                        }
                        is LdcInsnNode -> {
                            val cst = instruction.cst
                            if (cst is Type) {
                                cst.descriptor.modify {
                                    processingClass.markModified()
                                    instruction.cst = Type.getType(it)
                                }
                            }
                        }
                        is MultiANewArrayInsnNode -> {
                            instruction.desc.modify {
                                processingClass.markModified()
                                instruction.desc = it
                            }
                        }
                        is TypeInsnNode -> {
                            instruction.desc.modify {
                                processingClass.markModified()
                                instruction.desc = it
                            }
                        }
                        is InvokeDynamicInsnNode -> {
                            instruction.desc.modify {
                                processingClass.markModified()
                                instruction.desc = it
                            }
                            instruction.bsm?.modify {
                                processingClass.markModified()
                                instruction.bsm = it
                            }
                            instruction.bsmArgs?.forEachIndexed { index, arg ->
                                if (arg is Type) {
                                    arg.descriptor.modify {
                                        processingClass.markModified()
                                        instruction.bsmArgs[index] = Type.getType(it)
                                    }
                                } else if (arg is Handle) {
                                    arg.modify {
                                        processingClass.markModified()
                                        instruction.bsmArgs[index] = it
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return modified
    }
}

private const val FROM_INTERNAL_NAME = "fromInternalName"

private fun deleteAfterModification(module: ProcessingDirectory): Boolean {
    var modified = false
    module.classes.forEach {
        val changeClassName = it.node.invisibleAnnotations.find(ChangeClassName::class.java)
        if (changeClassName != null && changeClassName.getParameter(ChangeClassName::deleteAfterChanging.name) as Boolean? == true) {
            it.delete()
            modified = true
        }
    }
    return modified
}
