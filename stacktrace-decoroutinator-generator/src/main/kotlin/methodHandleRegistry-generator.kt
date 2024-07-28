@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.generator

import dev.reformator.stacktracedecoroutinator.runtime.BaseMethodHandleRegistry
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.util.concurrent.CopyOnWriteArrayList

internal object GeneratorMethodHandleRegistry: BaseMethodHandleRegistry() {
    private val classLoaderByRevision: MutableList<DecoroutinatorClassLoader> = CopyOnWriteArrayList()

    override fun generateStacktraceClass(
        className: String,
        fileName: String?,
        classRevision: Int,
        methodName2LineNumbers: Map<String, Set<Int>>
    ): Class<*> {
        val classBody = getClassBody(className, fileName, methodName2LineNumbers)
        while (classLoaderByRevision.size <= classRevision) {
            classLoaderByRevision.add(DecoroutinatorClassLoader())
        }
        return classLoaderByRevision[classRevision].defineClass(className, classBody)
    }
}

private fun getClassBody(
    className: String,
    fileName: String?,
    methodName2LineNumbers: Map<String, Set<Int>>
): ByteArray {
    val classNode = ClassNode(Opcodes.ASM9).apply {
        version = Opcodes.V1_8
        access = Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER
        name = className.replace('.', '/')
        superName = Type.getInternalName(java.lang.Object::class.java)
        sourceFile = fileName
    }
    classNode.methods = methodName2LineNumbers.entries.map { (methodName, lineNumbers) ->
        buildStacktraceMethodNode(methodName, lineNumbers, false)
    }
    val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
    classNode.accept(writer)
    return writer.toByteArray()
}