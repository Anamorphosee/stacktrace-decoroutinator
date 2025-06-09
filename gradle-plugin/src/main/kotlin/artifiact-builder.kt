@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.gradleplugin

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.ModuleNode
import org.objectweb.asm.tree.ModuleRequireNode
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.sequences.forEach

private val log = KotlinLogging.logger { }

internal typealias ArtifactPath = List<String>

internal interface ArtifactWalker {
    fun onFile(path: ArtifactPath, reader: () -> InputStream): Boolean
    fun onDirectory(path: ArtifactPath): Boolean
}

internal interface Artifact {
    fun walk(walker: ArtifactWalker)
    fun containsFile(filePath: ArtifactPath): Boolean =
        getFileReader(filePath) != null
    fun containsDirectory(directoryPath: ArtifactPath): Boolean {
        var found = false
        walk(object: ArtifactWalker {
            override fun onFile(path: ArtifactPath, reader: () -> InputStream): Boolean =
                true

            override fun onDirectory(path: ArtifactPath): Boolean {
                if (path == directoryPath) {
                    found = true
                    return false
                }
                return true
            }
        })
        return found
    }
    fun getFileReader(filePath: ArtifactPath): (() -> InputStream)? {
        var foundReader: (() -> InputStream)? = null
        walk(object: ArtifactWalker {
            override fun onFile(path: ArtifactPath, reader: () -> InputStream): Boolean {
                if (path == filePath) {
                    foundReader = reader
                    return false
                }
                return true
            }

            override fun onDirectory(path: ArtifactPath): Boolean =
                true
        })
        return foundReader
    }
}

internal interface ArtifactBuilder {
    fun addFile(path: ArtifactPath, body: InputStream)
    fun addDirectory(path: ArtifactPath)
}

@JvmInline internal value class ZipArtifact(private val zip: ZipFile): Artifact {
    override fun walk(walker: ArtifactWalker) {
        zip.entries().asSequence().forEach { entry ->
            val path = run {
                val segments = entry.name.split("/")
                if (entry.isDirectory) {
                    segments.dropLast(1)
                } else {
                    segments
                }
            }
            if (entry.isDirectory) {
                walker.onDirectory(path)
            } else {
                walker.onFile(path) { zip.getInputStream(entry) }
            }
        }
    }

    override fun containsFile(filePath: ArtifactPath): Boolean =
        zip.getEntry(filePath.joinToString(separator = "/")) != null

    override fun containsDirectory(directoryPath: ArtifactPath): Boolean =
        zip.getEntry(directoryPath.joinToString(separator = "/", postfix = "/")) != null

    override fun getFileReader(filePath: ArtifactPath): (() -> InputStream)? {
        val entry = zip.getEntry(filePath.joinToString(separator = "/"))
        if (entry == null) {
            return null
        }
        return {
            zip.getInputStream(entry)
        }
    }
}

internal class ZipArtifactBuilder(private val zip: ZipOutputStream): ArtifactBuilder {
    private val names = mutableSetOf<String>()
    override fun addFile(path: ArtifactPath, body: InputStream) {
        val name = path.joinToString(separator = "/")
        if (!names.add(name)) {
            log.warn { "Duplicate zip entry '$name'. Ignoring it" }
            return
        }
        val entry = ZipEntry(path.joinToString(separator = "/"))
        entry.method = ZipEntry.DEFLATED
        zip.putNextEntry(entry)
        body.copyTo(zip)
        zip.closeEntry()
    }

    override fun addDirectory(path: ArtifactPath) {
        val name = path.joinToString(separator = "/", postfix = "/")
        if (!names.add(name)) {
            log.warn { "Duplicate zip entry '$name'. Ignoring it" }
            return
        }
        val entry = ZipEntry(path.joinToString(separator = "/", postfix = "/"))
        zip.putNextEntry(entry)
        zip.closeEntry()
    }
}

@JvmInline internal value class DirectoryArtifact(private val root: File): Artifact, ArtifactBuilder {
    override fun walk(walker: ArtifactWalker) {
        root.walk().forEach { file ->
            val relativePath = file.relativeTo(root)
            if (file.isDirectory) {
                walker.onDirectory(relativePath.path.split(File.separator))
            } else {
                walker.onFile(relativePath.path.split(File.separator)) { file.inputStream() }
            }
        }
    }

    override fun containsFile(filePath: ArtifactPath): Boolean =
        filePath.fold(root) { acc, segment -> acc.resolve(segment) }.isFile

    override fun containsDirectory(directoryPath: ArtifactPath): Boolean =
        directoryPath.fold(root) { acc, segment -> acc.resolve(segment) }.isDirectory

    override fun getFileReader(filePath: ArtifactPath): (() -> InputStream)? {
        val file = filePath.fold(root) { acc, segment -> acc.resolve(segment) }
        if (!file.isFile) {
            return null
        }
        return {
            file.inputStream()
        }
    }

    override fun addFile(path: ArtifactPath, body: InputStream) {
        val file = path.fold(root) { acc, segment -> acc.resolve(segment) }
        file.outputStream().use { output ->
            body.copyTo(output)
        }
    }

    @Suppress("AssertionSideEffect")
    override fun addDirectory(path: ArtifactPath) {
        val file = path.fold(root) { acc, segment -> acc.resolve(segment) }
        assert(file.mkdir())
    }
}

internal val String.className2ArtifactPath: ArtifactPath
    get() {
        val segments = split('.')
        return buildList(segments.size) {
            segments.forEachIndexed { index, segment ->
                if (index == segments.lastIndex) {
                    add("$segment.class")
                } else {
                    add(segment)
                }
            }
        }
    }


internal val String.className2InternalName: String
    get() = replace('.', '/')

internal fun getClassNode(classBody: InputStream): ClassNode? {
    return try {
        val classReader = ClassReader(classBody)
        val classNode = ClassNode(Opcodes.ASM9)
        classReader.accept(classNode, ClassReader.SKIP_CODE)
        classNode
    } catch (e: Exception) {
        log.warn(e) { "Failed to read class from input stream" }
        null
    }
}

internal val ClassNode.classBody: ByteArray
    get() {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        accept(writer)
        return writer.toByteArray()
    }

internal fun tryReadModuleInfo(body: InputStream): ClassNode? {
    val classNode = getClassNode(body)
    if (classNode?.module == null) {
        log.warn { "'module-info.class' does not contain module information" }
        return null
    }
    return classNode
}

internal fun ModuleNode.addRequiresModule(moduleName: String) {
    if (requires == null) {
        requires = mutableListOf()
    }
    if (requires.any { it.module == moduleName }) {
        log.warn { "Module '$moduleName' is already required by module '$name'" }
        return
    }
    requires.add(ModuleRequireNode(moduleName, Opcodes.ACC_SYNTHETIC, null))
}