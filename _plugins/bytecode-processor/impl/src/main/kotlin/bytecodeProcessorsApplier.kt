@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.impl

import dev.reformator.bytecodeprocessor.pluginapi.ProcessingClass
import dev.reformator.bytecodeprocessor.pluginapi.ProcessingDirectory
import dev.reformator.bytecodeprocessor.pluginapi.ProcessingModule
import dev.reformator.bytecodeprocessor.pluginapi.Processor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.ModuleNode
import java.io.File

fun applyBytecodeProcessors(processors: Set<Processor>, classesDir: File) {
    require(classesDir.isDirectory)

    var modificationCounter = 0
    val updateModificationCounter: () -> Unit = { modificationCounter++ }

    val processingClasses = mutableSetOf<ProcessingClassImpl>()
    var processingModule: ProcessingModuleImpl? = null

    classesDir.walk().forEach { file ->
        if (!file.isFile || file.extension != "class") return@forEach

        val classNode = file.readClassNode() ?: return@forEach

        if (classNode.file != file.relativeTo(classesDir)) return@forEach

        if (classNode.module != null) {
            if (processingModule != null) {
                error("duplicate processing module")
            }
            processingModule = ProcessingModuleImpl(
                file = file,
                classNode = classNode,
                updateModificationCounter = updateModificationCounter
            )
        } else {
            processingClasses.add(ProcessingClassImpl(
                file = file,
                originalInternalName = classNode.name,
                node = classNode,
                updateModificationCounter = updateModificationCounter
            ))
        }
    }

    val processingDirectory = object: ProcessingDirectory {
        override val classes: Set<ProcessingClass>
            get() = processingClasses

        override val module: ProcessingModule?
            get() = processingModule
    }

    var previousModificationCounter: Int
    do {
        previousModificationCounter = modificationCounter

        processors.forEach { it.process(processingDirectory) }

        val iter = processingClasses.iterator()
        while (iter.hasNext()) {
            val clazz = iter.next()
            if (clazz.state == ProcessingFileState.DELETED) {
                require(clazz.file.delete())
                iter.remove()
            } else if (clazz.state == ProcessingFileState.MODIFIED) {
                clazz.modified = true
                clazz.state = ProcessingFileState.UNMODIFIED
            }
        }
    } while (previousModificationCounter != modificationCounter)

    processingClasses.forEach { clazz ->
        if (clazz.modified) {
            val file = if (clazz.originalInternalName == clazz.node.name) {
                clazz.file
            } else {
                require(clazz.file.delete())
                val file = classesDir.resolve(clazz.node.file)
                file.parentFile.mkdirs()
                file
            }
            clazz.node.writeTo(file)
        } else {
            require(clazz.originalInternalName == clazz.node.name)
        }
    }

    processingModule?.let {
        if (it.modified) {
            it.classNode.writeTo(it.file)
        }
    }
}

private class ProcessingClassImpl(
    val file: File,
    val originalInternalName: String,
    override val node: ClassNode,
    private val updateModificationCounter: () -> Unit
): ProcessingClass {
    var state: ProcessingFileState = ProcessingFileState.UNMODIFIED
    var modified = false

    override fun markModified() {
        when (state) {
            ProcessingFileState.UNMODIFIED -> state = ProcessingFileState.MODIFIED
            ProcessingFileState.MODIFIED -> { }
            ProcessingFileState.DELETED -> error("trying to modify deleted class [${node.name}]")
        }
        updateModificationCounter()
    }

    override fun delete() {
        when (state) {
            ProcessingFileState.UNMODIFIED -> state = ProcessingFileState.DELETED
            ProcessingFileState.MODIFIED -> error("trying to delete modified class [${node.name}]")
            ProcessingFileState.DELETED -> error("trying to delete already deleted class [${node.name}]")
        }
        updateModificationCounter()
    }
}

private class ProcessingModuleImpl(
    val file: File,
    val classNode: ClassNode,
    private val updateModificationCounter: () -> Unit
): ProcessingModule {
    var modified = false

    override val node: ModuleNode
        get() = classNode.module

    override fun markModified() {
        modified = true
        updateModificationCounter()
    }
}

private enum class ProcessingFileState {
    UNMODIFIED, MODIFIED, DELETED
}

private fun File.readClassNode(): ClassNode? =
    inputStream().use { input ->
        try {
            val classReader = ClassReader(input)
            val classNode = ClassNode(Opcodes.ASM9)
            classReader.accept(classNode, 0)
            classNode
        } catch (_: Exception) {
            null
        }
    }

private val ClassNode.file: File
    get() {
        val segments = name.split('/')
        return if (segments.size == 1) {
            File(segments[0] + ".class")
        } else if (segments.size > 1) {
            var path = File(segments[0])
            for (i in 1 until segments.lastIndex) {
                path = path.resolve(segments[i])
            }
            path.resolve(segments.last() + ".class")
        } else {
            error("no segments")
        }
    }

fun ClassNode.writeTo(file: File) {
    val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
    accept(writer)
    file.outputStream().use { it.write(writer.toByteArray()) }
}
