@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("GradleClassTransformerKt")

package dev.reformator.stacktracedecoroutinator.gradleplugin

import dev.reformator.stacktracedecoroutinator.generator.internal.addReadProviderModuleToModuleInfo
import dev.reformator.stacktracedecoroutinator.generator.internal.getDebugMetadataInfoFromClassBody
import dev.reformator.stacktracedecoroutinator.generator.internal.transformClassBody
import io.github.oshai.kotlinlogging.KotlinLogging
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.sequences.forEach

private const val CLASS_EXTENSION = ".class"
private const val MODULE_INFO_CLASS_NAME = "module-info.class"
private val log = KotlinLogging.logger { }

abstract class DecoroutinatorTransformAction: TransformAction<DecoroutinatorTransformAction.Parameters> {
    interface Parameters: TransformParameters {
        @get:Input
        val skipSpecMethods: Property<Boolean>
    }

    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val root = inputArtifact.get().asFile
        log.debug { "trying transform artifact [${root.absolutePath}]" }
        if (root.isFile) {
            log.debug { "artifact [${root.absolutePath}] is a file" }
            val needModification = run {
                try {
                    transformZip(
                        zip = root,
                        skipSpecMethods = parameters.skipSpecMethods.get(),
                        putNextEntry = { },
                        putFileBody = { modified, _ ->
                            if (modified) {
                                return@run true
                            }
                        },
                        closeEntry = { }
                    )
                } catch (_: IOException) { }
                false
            }
            if (needModification) {
                val suffix = root.name.lastIndexOf('.').let { index ->
                    if (index == -1) "" else root.name.substring(index)
                }
                val newName = root.name.removeSuffix(suffix) + "-decoroutinator" + suffix
                val newFile = outputs.file(newName)
                ZipOutputStream(newFile.outputStream()).use { output ->
                    transformZip(
                        zip = root,
                        skipSpecMethods = parameters.skipSpecMethods.get(),
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
                    skipSpecMethods = parameters.skipSpecMethods.get(),
                    onDirectory = { },
                    onFile = { _, _, modified ->
                        if (modified) return@run true
                    }
                )
                false
            }
            if (needModification) {
                val newRoot = outputs.dir(root.name + "-decoroutinator")
                transformClassesDir(
                    root = root,
                    skipSpecMethods = parameters.skipSpecMethods.get(),
                    onDirectory = { newRoot.resolve(it).mkdir() },
                    onFile = { relativePath, content, _ ->
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
    skipSpecMethods: Boolean,
    putNextEntry: (ZipEntry) -> Unit,
    putFileBody: (modified: Boolean, body: InputStream) -> Unit,
    closeEntry: () -> Unit
) {
    val names = mutableSetOf<String>()
    ZipFile(zip).use { input ->
        var readProviderModule = false

        input.entries().asSequence().forEach { entry: ZipEntry ->
            if (entry.isDirectory || !entry.name.isModuleInfo) {
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
                    var newBody: ByteArray? = null
                    if (entry.name.isClass) {
                        val transformationStatus = input.getInputStream(entry).use { classBody ->
                            transformClassBody(
                                classBody = classBody,
                                metadataResolver = metadataResolver@{ metadataClassName ->
                                    val entryName = metadataClassName.replace('.', '/') + CLASS_EXTENSION
                                    val classEntry = input.getEntry(entryName) ?: return@metadataResolver null
                                    input.getInputStream(classEntry).use {
                                        getDebugMetadataInfoFromClassBody(it)
                                    }
                                },
                                skipSpecMethods = skipSpecMethods
                            )
                        }
                        readProviderModule = readProviderModule || transformationStatus.needReadProviderModule
                        newBody = transformationStatus.updatedBody
                    }
                    val modified = newBody != null
                    (if (modified) ByteArrayInputStream(newBody) else input.getInputStream(entry)).use { body ->
                        putFileBody(modified, body)
                    }
                }
                closeEntry()
            }
        }

        input.entries().asSequence().forEach { entry: ZipEntry ->
            if (!entry.isDirectory && entry.name.isModuleInfo) {
                if (!names.add(entry.name)) {
                    log.warn { "Duplicate module-info entry '${entry.name}' in file '$zip'. Ignoring it" }
                    return@forEach
                }
                putNextEntry(ZipEntry(entry.name).apply {
                    entry.lastModifiedTime?.let { lastModifiedTime = it }
                    entry.lastAccessTime?.let { lastAccessTime = it }
                    entry.creationTime?.let { creationTime = it }
                    method = ZipEntry.DEFLATED
                    comment = entry.comment
                })
                var newBody: ByteArray? = null
                if (readProviderModule) {
                    newBody = input.getInputStream(entry).use { moduleInfoBody ->
                        addReadProviderModuleToModuleInfo(moduleInfoBody)
                    }
                }
                val modified = newBody != null
                (if (modified) newBody!!.inputStream() else input.getInputStream(entry)).use { body ->
                    putFileBody(modified, body)
                }
                closeEntry()
            }
        }
    }
}

internal inline fun transformClassesDir(
    root: File,
    skipSpecMethods: Boolean,
    onDirectory: (relativePath: File) -> Unit,
    onFile: (relativePath: File, content: InputStream, modified: Boolean) -> Unit
) {
    var readProviderModule = false

    root.walk().forEach { file ->
        val relativePath = file.relativeTo(root)
        if (file.isFile && file.isClass) {
            val transformationStatus = file.inputStream().use { classBody ->
                transformClassBody(
                    classBody = classBody,
                    metadataResolver = { metadataClassName ->
                        val metadataClassRelativePath = metadataClassName.replace('.', File.separatorChar) + CLASS_EXTENSION
                        val classPath = root.resolve(metadataClassRelativePath)
                        if (classPath.isFile) {
                            classPath.inputStream().use {
                                getDebugMetadataInfoFromClassBody(it)
                            }
                        } else {
                            null
                        }
                    },
                    skipSpecMethods = skipSpecMethods
                )
            }
            readProviderModule = readProviderModule || transformationStatus.needReadProviderModule
            transformationStatus.updatedBody?.let {
                onFile(relativePath, it.inputStream(), true)
                return@forEach
            }
        }
        if (file.isDirectory) {
            onDirectory(relativePath)
            return@forEach
        }
        if (!file.isModuleInfo) {
            file.inputStream().use { input ->
                onFile(relativePath, input, false)
            }
        }
    }

    visitModuleInfoFiles(root) { path, relativePath ->
        var newBody: ByteArray? = null
        if (readProviderModule) {
            newBody = path.inputStream().use { addReadProviderModuleToModuleInfo(it) }
        }
        val modified = newBody != null
        onFile(relativePath, if (modified) newBody!!.inputStream() else path.inputStream(), modified)
    }
}

internal inline fun visitModuleInfoFiles(root: File, onModuleInfoFile: (path: File, relativePath: File) -> Unit) {
    root.walk().forEach { file ->
        if (file.isFile && file.isModuleInfo) {
            val relativePath = file.relativeTo(root)
            onModuleInfoFile(file, relativePath)
        }
    }
}

private val String.isModuleInfo: Boolean
    get() = substringAfterLast('/') == MODULE_INFO_CLASS_NAME

private val String.isClass: Boolean
    get() = endsWith(CLASS_EXTENSION) && !isModuleInfo

private val File.isModuleInfo: Boolean
    get() = name == MODULE_INFO_CLASS_NAME

private val File.isClass: Boolean
    get() = name.endsWith(CLASS_EXTENSION) && !isModuleInfo
