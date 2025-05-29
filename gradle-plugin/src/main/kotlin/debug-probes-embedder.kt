@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("DebugProbesEmbedderKt")

package dev.reformator.stacktracedecoroutinator.gradleplugin

import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import io.github.oshai.kotlinlogging.KotlinLogging
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

private val log = KotlinLogging.logger { }

private const val DEBUG_PROBES_CLASS_NAME = "kotlin.coroutines.jvm.internal.DebugProbesKt"
private val debugProbesClassZipEntryName = DEBUG_PROBES_CLASS_NAME.replace('.', '/') + ".class"
private val debugProbesClassRelativeFile = DEBUG_PROBES_CLASS_NAME.split('.').let { segments ->
    segments.subList(1, segments.size - 1).fold(File(segments[0])) { acc, segment ->
        acc.resolve(segment)
    }.resolve(segments.last() + ".class")
}

val decoroutinatorEmbeddedDebugProbesAttribute: Attribute<Boolean> = Attribute.of(
    "dev.reformator.stacktracedecoroutinator.embeddedDebugProbes",
    Boolean::class.javaObjectType
)

abstract class DecoroutinatorEmbedDebugProbesAction: TransformAction<TransformParameters.None> {
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val root = inputArtifact.get().asFile
        log.debug { "trying embed DP for artifact [${root.absolutePath}]" }
        if (root.isFile) {
            log.debug { "artifact [${root.absolutePath}] is a file" }
            val needModification = run {
                try {
                    transformZip(
                        zip = root,
                        putNextEntry = { },
                        putFileBody = { modified, _ ->
                            if (modified) {
                                return@run true
                            }
                        },
                        closeEntry = { }
                    )
                } catch (e: IOException) {
                    log.debug(e) { "Failed to read zip file [${root.absolutePath}]" }
                }
                false
            }
            if (needModification) {
                val newName = run {
                    val suffix = root.name.lastIndexOf('.').let { index ->
                        if (index == -1) "" else root.name.substring(index)
                    }
                    root.name.removeSuffix(suffix) + "-embed-dep-props" + suffix
                }
                val newFile = outputs.file(newName)
                ZipOutputStream(newFile.outputStream()).use { output ->
                    transformZip(
                        zip = root,
                        putNextEntry = { output.putNextEntry(it) },
                        putFileBody = { _, body -> body.copyTo(output) },
                        closeEntry = { output.closeEntry() }
                    )
                }
                log.debug { "file artifact [${root.absolutePath}] was transformed to [${newFile.absolutePath}]" }
            } else {
                log.debug { "file artifact [${root.absolutePath}] was skipped" }
                outputs.file(inputArtifact)
            }
        } else if (root.isDirectory) {
            log.debug { "artifact [${root.absolutePath}] is a directory" }
            val needModification = run {
                transformClassesDir(
                    root = root,
                    onDirectory = { },
                    onFile = { modified, _, _ ->
                        if (modified) return@run true
                    }
                )
                false
            }
            if (needModification) {
                val newRoot = outputs.dir(root.name + "-embed-dep-props")
                transformClassesDir(
                    root = root,
                    onDirectory = { newRoot.resolve(it).mkdir() },
                    onFile = { _, relativePath, content ->
                        newRoot.resolve(relativePath).outputStream().use { output ->
                            content.copyTo(output)
                        }
                    }
                )
                log.debug { "directory artifact [${root.absolutePath}] was transformed to [${newRoot.absolutePath}]" }
            } else {
                log.debug { "directory artifact [${root.absolutePath}] was skipped" }
                outputs.dir(inputArtifact)
            }
        } else {
            log.debug { "artifact [${root.absolutePath}] does not exist" }
        }
    }
}

private inline fun transformZip(
    zip: File,
    putNextEntry: (ZipEntry) -> Unit,
    putFileBody: (modified: Boolean, body: InputStream) -> Unit,
    closeEntry: () -> Unit
) {
    val names = mutableSetOf<String>()
    ZipFile(zip).use { input ->
        input.entries().asSequence().forEach { entry: ZipEntry ->
            if (!names.add(entry.name)) {
                log.warn { "Duplicate zip entry '${entry.name}' in file '$zip'. Ignoring it" }
                return@forEach
            }
            putNextEntry(ZipEntry(entry.name).apply {
                entry.lastModifiedTime?.let { lastModifiedTime = it }
                entry.lastAccessTime?.let { lastAccessTime = it }
                entry.creationTime?.let { creationTime = it }
                method = ZipEntry.DEFLATED
                comment = entry.comment
            })
            if (!entry.isDirectory) {
                if (entry.name == debugProbesClassZipEntryName) {
                    putFileBody(true, getEmbeddedDebugProbesKtClassBodyStream())
                } else {
                    putFileBody(false, input.getInputStream(entry))
                }
            }
            closeEntry()
        }
    }
}

private inline fun transformClassesDir(
    root: File,
    onDirectory: (relativePath: File) -> Unit,
    onFile: (modified: Boolean, relativePath: File, content: InputStream) -> Unit
) {
    root.walk().forEach { file ->
        val relativePath = file.relativeTo(root)
        when {
            file.isDirectory -> onDirectory(relativePath)
            file == debugProbesClassRelativeFile -> onFile(true, relativePath, getEmbeddedDebugProbesKtClassBodyStream())
            else -> onFile(false, relativePath, file.inputStream())
        }
    }
}

private val embeddedDebugProbesKtClassBodyBase64: String
    @LoadConstant get() = fail()

private fun getEmbeddedDebugProbesKtClassBodyStream() =
    ByteArrayInputStream(Base64.getDecoder().decode(embeddedDebugProbesKtClassBodyBase64))
