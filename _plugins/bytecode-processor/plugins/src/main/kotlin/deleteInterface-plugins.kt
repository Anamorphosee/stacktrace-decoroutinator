@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.plugins

import dev.reformator.bytecodeprocessor.intrinsics.DeleteInterface
import dev.reformator.bytecodeprocessor.pluginapi.ProcessingDirectory
import dev.reformator.bytecodeprocessor.pluginapi.Processor
import dev.reformator.bytecodeprocessor.plugins.internal.find
import dev.reformator.bytecodeprocessor.plugins.internal.getParameter
import dev.reformator.bytecodeprocessor.plugins.internal.setParameter
import org.objectweb.asm.Type

object DeleteInterfaceProcessor: Processor {
    override fun process(directory: ProcessingDirectory) {
        directory.classes.forEach { clazz ->
            val annotation = clazz.node.invisibleAnnotations.find(DeleteInterface::class.java)
            if (annotation != null) {
                if (annotation.getParameter(APPLIED_PARAMETER) as Boolean? != true) {
                    val value = annotation.getParameter(DeleteInterface::value.name) as Type
                    require(value.internalName in clazz.node.interfaces)
                    clazz.node.interfaces.remove(value.internalName)
                    annotation.setParameter(APPLIED_PARAMETER, true)
                    clazz.markModified()
                }
            }
        }
    }
}

private const val APPLIED_PARAMETER = "applied"