@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.bytecodeprocessor.pluginapi

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.ModuleNode
import java.io.File

fun File.applyBytecodeProcessors(
    processors: Collection<Processor>,
    context: BytecodeProcessorContext = BytecodeProcessorContextImpl(),
    skipUpdate: Boolean = false
) {
    require(isDirectory)

    var modificationCounter = 0
    val updateModificationCounter: () -> Unit = { modificationCounter++ }

    val processingClasses = mutableListOf<ProcessingClassImpl>()
    var processingModule: ProcessingModuleImpl? = null

    walk().forEach { file ->
        if (!file.isFile || file.extension != "class") return@forEach

        val classNode = file.readClassNode() ?: return@forEach

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
        override val classes: Sequence<ProcessingClass>
            get() = processingClasses.asSequence().filter { it.state != ProcessingFileState.DELETED }

        override val module: ProcessingModule?
            get() = processingModule
    }

    var previousModificationCounter: Int
    val filesToDelete: MutableCollection<File> = arrayListOf()
    do {
        previousModificationCounter = modificationCounter

        processors.forEach { it.process(processingDirectory, context) }

        val iter = processingClasses.iterator()
        while (iter.hasNext()) {
            val clazz = iter.next()
            if (clazz.state == ProcessingFileState.DELETED) {
                filesToDelete.add(clazz.file)
                iter.remove()
            } else if (clazz.state == ProcessingFileState.MODIFIED) {
                clazz.modified = true
                clazz.state = ProcessingFileState.UNMODIFIED
            }
        }
    } while (previousModificationCounter != modificationCounter)

    if (!skipUpdate) {
        filesToDelete.forEach { require(it.delete()) }

        processingClasses.forEach { clazz ->
            if (clazz.modified) {
                val file = if (clazz.originalInternalName == clazz.node.name) {
                    clazz.file
                } else {
                    require(clazz.file.delete())
                    val file = resolve(clazz.node.file)
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
