@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.plugins

import dev.reformator.bytecodeprocessor.intrinsics.AddToStaticInitializer
import dev.reformator.bytecodeprocessor.pluginapi.ProcessingDirectory
import dev.reformator.bytecodeprocessor.pluginapi.Processor
import dev.reformator.bytecodeprocessor.plugins.internal.*
import dev.reformator.bytecodeprocessor.plugins.internal.find
import dev.reformator.bytecodeprocessor.plugins.internal.getParameter
import dev.reformator.bytecodeprocessor.plugins.internal.isStatic
import dev.reformator.bytecodeprocessor.plugins.internal.setParameter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.MethodInsnNode

object AddToStaticInitializerProcessor: Processor {
    override fun process(directory: ProcessingDirectory) {
        directory.classes.forEach { clazz ->
            clazz.node.methods?.forEachAllowingAddingToEnd { method ->
                val annotation = method.invisibleAnnotations.find(AddToStaticInitializer::class.java)
                if (annotation != null && annotation.getParameter(APPLIED_PARAMETER) as Boolean? != true) {
                    val descriptor = Type.getMethodType(method.desc)
                    require(method.isStatic)
                    require(descriptor.argumentCount == 0)
                    require(descriptor.returnType == Type.VOID_TYPE)

                    val clinit = clazz.node.getOrCreateClinit()
                    clinit.insertBefore(MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        clazz.node.name,
                        method.name,
                        method.desc
                    ))

                    annotation.setParameter(APPLIED_PARAMETER, true)
                    clazz.markModified()
                }
            }
        }
    }
}

private const val APPLIED_PARAMETER = "applied"
