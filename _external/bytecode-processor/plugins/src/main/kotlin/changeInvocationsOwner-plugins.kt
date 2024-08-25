@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.plugins

import dev.reformator.bytecodeprocessor.intrinsics.ChangeInvocationsOwner
import dev.reformator.bytecodeprocessor.pluginapi.ProcessingDirectory
import dev.reformator.bytecodeprocessor.pluginapi.Processor
import dev.reformator.bytecodeprocessor.plugins.internal.find
import dev.reformator.bytecodeprocessor.plugins.internal.getParameter
import dev.reformator.bytecodeprocessor.plugins.internal.internalName
import org.objectweb.asm.Type
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

object ChangeInvocationsOwnerProcessor: Processor {
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
        val newOwnerInternalNamesByMethod = module.classes.asSequence().flatMap { processingClass ->
            processingClass.node.methods.orEmpty().asSequence().mapNotNull { method ->
                val annotation = method.invisibleAnnotations.find(ChangeInvocationsOwner::class.java) ?: return@mapNotNull null

                val toParameter = annotation.getParameter(ChangeInvocationsOwner::to.name) as Type?
                val toNameParameter = annotation.getParameter(ChangeInvocationsOwner::toName.name) as String?
                if (toParameter != null && toNameParameter != null) {
                    error("both parameters [${ChangeInvocationsOwner::to.name}] and [${ChangeInvocationsOwner::toName.name}] were set")
                }

                val toInternalName: String = when {
                    toParameter != null -> toParameter.internalName
                    toNameParameter != null -> toNameParameter.internalName
                    else -> error("both parameters [${ChangeInvocationsOwner::to.name}] and [${ChangeInvocationsOwner::toName.name}] weren't set")
                }

                require(toNameParameter != processingClass.node.name)

                val key = Key(
                    ownerInternalName = processingClass.node.name,
                    method = method
                )

                key to toInternalName
            }
        }.toMap()

        var modified = false

        module.classes.forEach { processingClass ->
            processingClass.node.methods?.forEach { method ->
                method.instructions?.forEach {
                    if (it is MethodInsnNode) {
                        val key = Key(
                            ownerInternalName = it.owner,
                            name = it.name,
                            descriptor = it.desc
                        )
                        val newOwnerInternalName = newOwnerInternalNamesByMethod[key]
                        if (newOwnerInternalName != null) {
                            it.owner = newOwnerInternalName
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
            val annotation = method.invisibleAnnotations.find(ChangeInvocationsOwner::class.java) ?: continue
            if (annotation.getParameter(ChangeInvocationsOwner::deleteAfterChanging.name) as Boolean? == true) {
                iter.remove()
                processingClass.markModified()
                modified = true
            }
        }
    }
    return modified
}
