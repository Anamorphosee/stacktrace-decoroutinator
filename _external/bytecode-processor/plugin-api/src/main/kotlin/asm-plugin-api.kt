@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.pluginapi

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.ModuleNode

interface ProcessingClass {
    val node: ClassNode
    fun markModified()
    fun delete()
}

interface ProcessingModule {
    val node: ModuleNode
    fun markModified()
}

interface ProcessingDirectory {
    val classes: Set<ProcessingClass>
    val module: ProcessingModule?
}

fun interface Processor {
    fun process(directory: ProcessingDirectory)
}
