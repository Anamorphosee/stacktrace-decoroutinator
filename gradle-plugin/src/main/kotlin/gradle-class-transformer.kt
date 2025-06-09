@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("GradleClassTransformerKt")

package dev.reformator.stacktracedecoroutinator.gradleplugin

import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.common.internal.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.generator.internal.PROVIDER_MODULE_NAME
import dev.reformator.stacktracedecoroutinator.generator.internal.getDebugMetadataInfoFromClassBody
import dev.reformator.stacktracedecoroutinator.generator.internal.transformClassBody
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorBaseContinuationAccessorProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ModuleProvideNode
import java.io.IOException
import java.io.InputStream
import java.util.Base64
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.reflect.jvm.jvmName

private const val CLASS_EXTENSION = ".class"
internal const val MODULE_INFO_CLASS_NAME = "module-info.class"
private const val BASE_CONTINUATION_ACCESSOR_IMPL_CLASS_NAME =
    "kotlin.coroutines.jvm.internal.DecoroutinatorBaseContinuationAccessorImpl"
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
            val artifact = try {
                ZipArtifact(ZipFile(root))
            } catch (e: IOException) {
                log.warn(e) { "Failed to read artifact [${root.absolutePath}]. It will be skipped." }
                null
            }
            val needModification = run {
                if (artifact == null) {
                    log.warn { "Artifact [${root.absolutePath}] is not a valid zip file. It will be skipped." }
                    return@run false
                }
                try {
                    var needModification = false
                    artifact.transform(
                        skipSpecMethods = parameters.skipSpecMethods.get(),
                        onFile = { modified, _, _ ->
                            if (modified) {
                                needModification = true
                                false
                            } else {
                                true
                            }
                        },
                        onDirectory = { _ -> }
                    )
                    needModification
                } catch (e: IOException) {
                    log.warn(e) { "Failed to read artifact [${root.absolutePath}]. It will be skipped." }
                    false
                }
            }
            if (needModification) {
                val suffix = root.name.lastIndexOf('.').let { index ->
                    if (index == -1) "" else root.name.substring(index)
                }
                val newName = root.name.removeSuffix(suffix) + "-decoroutinator" + suffix
                val newFile = outputs.file(newName)
                ZipOutputStream(newFile.outputStream()).use { output ->
                    artifact!!.transformTo(
                        skipSpecMethods = parameters.skipSpecMethods.get(),
                        builder = ZipArtifactBuilder(output)
                    )
                }
                log.debug { "file artifact [${root.absolutePath}] was transformed to [${newFile.absolutePath}]" }
            } else {
                log.debug { "file artifact [${root.absolutePath}] was skipped" }
                outputs.file(inputArtifact)
            }
        } else if (root.isDirectory) {
            log.debug { "artifact [${root.absolutePath}] is a directory" }
            val artifact = DirectoryArtifact(root)
            val needModification = run {
                var needModification = false
                artifact.transform(
                    skipSpecMethods = parameters.skipSpecMethods.get(),
                    onFile = { modified, _, _ ->
                        if (modified) {
                            needModification = true
                            false
                        } else {
                            true
                        }
                    },
                    onDirectory = { _ -> }
                )
                needModification
            }
            if (needModification) {
                val newRoot = outputs.dir(root.name + "-decoroutinator")
                artifact.transformTo(
                    skipSpecMethods = parameters.skipSpecMethods.get(),
                    builder = DirectoryArtifact(newRoot)
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

private fun Artifact.transformTo(skipSpecMethods: Boolean, builder: ArtifactBuilder) {
    transform(
        skipSpecMethods = skipSpecMethods,
        onFile = { _, path, body ->
            builder.addFile(path, body)
            true
        },
        onDirectory = { path ->
            builder.addDirectory(path)
        }
    )
}

internal fun Artifact.transform(
    skipSpecMethods: Boolean,
    onFile: (modified: Boolean, path: ArtifactPath, body: InputStream) -> Boolean,
    onDirectory: (path: ArtifactPath) -> Unit
) {
    val baseContinuationPath = BASE_CONTINUATION_CLASS_NAME.className2ArtifactPath
    var readProviderModule = false
    var containsBaseContinuation = false
    var stop = false

    walk(object: ArtifactWalker {
        override fun onFile(path: ArtifactPath, reader: () -> InputStream): Boolean {
            if (!path.isModuleInfo) {
                var newBody: ByteArray? = null
                if (path.isClass) {
                    containsBaseContinuation = containsBaseContinuation || path == baseContinuationPath
                    val transformationStatus = reader().use { classBody ->
                        transformClassBody(
                            classBody = classBody,
                            metadataResolver = { metadataClassName ->
                                val packageDepth = metadataClassName.count { it == '.' }
                                val metadataClassPath = metadataClassName
                                    .splitToSequence('.')
                                    .mapIndexed { index, component ->
                                        if (index < packageDepth) {
                                            component
                                        } else {
                                            "$component$CLASS_EXTENSION"
                                        }
                                    }
                                    .toList()
                                getFileReader(metadataClassPath)?.let { reader ->
                                    reader().use {
                                        getDebugMetadataInfoFromClassBody(it)
                                    }
                                }
                            },
                            skipSpecMethods = skipSpecMethods
                        )
                    }
                    readProviderModule = readProviderModule || transformationStatus.needReadProviderModule
                    newBody = transformationStatus.updatedBody
                }
                (newBody?.inputStream() ?: reader()).use { input ->
                    if (!onFile(newBody != null, path, input)) {
                        stop = true
                        return false
                    }
                }
            }
            return true
        }

        override fun onDirectory(path: ArtifactPath): Boolean {
            onDirectory(path)
            return true
        }
    })
    if (stop) return

    if (containsBaseContinuation) {
        assert(readProviderModule)
    }

    walk(object: ArtifactWalker {
        override fun onFile(path: ArtifactPath, reader: () -> InputStream): Boolean {
            if (path.isModuleInfo) {
                var newBody: ByteArray? = null
                if (readProviderModule) {
                    newBody = reader().use { moduleInfoBody ->
                        val node = tryReadModuleInfo(moduleInfoBody) ?: return@use null
                        node.module.addRequiresModule(PROVIDER_MODULE_NAME)
                        if (containsBaseContinuation) {
                            val provides: MutableList<ModuleProvideNode> = node.module.provides ?: run {
                                val provides = mutableListOf<ModuleProvideNode>()
                                node.module.provides = provides
                                provides
                            }
                            provides.add(ModuleProvideNode(
                                Type.getInternalName(DecoroutinatorBaseContinuationAccessorProvider::class.java),
                                listOf(BASE_CONTINUATION_ACCESSOR_IMPL_CLASS_NAME.className2InternalName)
                            ))
                        }
                        node.classBody
                    }
                }
                (newBody?.inputStream() ?: reader()).use { input ->
                    if (!onFile(newBody != null, path, input)) {
                        stop = true
                        return false
                    }
                }
            }
            return true
        }

        override fun onDirectory(path: ArtifactPath): Boolean = true
    })
    if (stop) return

    if (containsBaseContinuation) {
        Base64.getDecoder().decode(baseContinuationAccessorImplBodyBase64).inputStream().use { input ->
            if (!onFile(true, BASE_CONTINUATION_ACCESSOR_IMPL_CLASS_NAME.className2ArtifactPath, input)) {
                return
            }
        }
        val metaInfDirPath = listOf("META-INF")
        if (!containsDirectory(metaInfDirPath)) {
            onDirectory(metaInfDirPath)
        }
        val servicesDirPath = metaInfDirPath + "services"
        if (!containsDirectory(servicesDirPath)) {
            onDirectory(servicesDirPath)
        }
        BASE_CONTINUATION_ACCESSOR_IMPL_CLASS_NAME.toByteArray().inputStream().use { input ->
            if (!onFile(true, servicesDirPath + DecoroutinatorBaseContinuationAccessorProvider::class.jvmName, input)) {
                return
            }
        }
    }
}

private val ArtifactPath.isModuleInfo: Boolean
    get() = last() == MODULE_INFO_CLASS_NAME

private val ArtifactPath.isClass: Boolean
    get() = last().endsWith(CLASS_EXTENSION) && !isModuleInfo

private val baseContinuationAccessorImplBodyBase64: String
    @LoadConstant get() { fail() }
