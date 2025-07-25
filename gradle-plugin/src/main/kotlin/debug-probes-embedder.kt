@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("DebugProbesEmbedderKt")

package dev.reformator.stacktracedecoroutinator.gradleplugin

import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.runtimesettings.DecoroutinatorRuntimeSettingsProvider
import org.objectweb.asm.tree.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Base64
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.reflect.jvm.jvmName

private val log = KotlinLogging.logger { }

private const val DEBUG_PROBES_CLASS_NAME = "kotlin.coroutines.jvm.internal.DebugProbesKt"
private const val DEBUG_PROBES_PROVIDER_CLASS_NAME = "kotlin.coroutines.jvm.internal.DecoroutinatorDebugProbesProvider"
private const val DEBUG_PROBES_IMPL_CLASS_NAME = "kotlinx.coroutines.debug.internal.DebugProbesImpl"
private const val DEBUG_PROBES_PROVIDER_IMPL_CLASS_NAME = "kotlinx.coroutines.debug.internal.DecoroutinatorDebugProbesProviderImpl"
private const val DEBUG_PROBES_PROVIDER_UTILS_CLASS_NAME = "kotlinx.coroutines.debug.internal.DecoroutinatorDebugProbesProviderUtilsKt"

private val ArtifactPath.isModuleInfo: Boolean
    get() = lastOrNull() == "module-info.class"

private inline fun Artifact.processArtifact(
    skipArtifact: () -> Unit,
    modifyArtifact: ((newArtifact: ArtifactBuilder) -> Unit) -> Unit
) {
    val debugProbesPath = DEBUG_PROBES_CLASS_NAME.className2ArtifactPath
    val debugProbesImplPath = DEBUG_PROBES_IMPL_CLASS_NAME.className2ArtifactPath
    if (containsFile(debugProbesPath)) {
        modifyArtifact { newArtifact ->
            walk(object: ArtifactWalker {
                override fun onFile(path: ArtifactPath, reader: () -> InputStream): Boolean {
                    if (path == debugProbesPath) {
                        newArtifact.addFile(debugProbesPath, getDebugProbesKtClassBodyStream())
                        return true
                    }
                    if (path.isModuleInfo) {
                        val moduleInfo = reader().use { tryReadModuleInfo(it) }
                        if (moduleInfo != null) {
                            val uses: MutableList<String> = moduleInfo.module.uses ?: run {
                                val uses = mutableListOf<String>()
                                moduleInfo.module.uses = uses
                                uses
                            }
                            uses.add(DEBUG_PROBES_PROVIDER_CLASS_NAME.className2InternalName)
                            newArtifact.addFile(path, moduleInfo.classBody.inputStream())
                            return true
                        }
                    }
                    reader().use { newArtifact.addFile(path, it) }
                    return true
                }

                override fun onDirectory(path: ArtifactPath): Boolean {
                    newArtifact.addDirectory(path)
                    return true
                }
            })
            newArtifact.addFile(
                path = DEBUG_PROBES_PROVIDER_CLASS_NAME.className2ArtifactPath,
                body = getDebugProbesProviderClassBodyStream()
            )
        }
    } else if (containsFile(debugProbesImplPath)) {
        modifyArtifact { newArtifact ->
            walk(object: ArtifactWalker {
                override fun onFile(path: ArtifactPath, reader: () -> InputStream): Boolean {
                    if (path.isModuleInfo) {
                        val moduleInfo = reader().use { tryReadModuleInfo(it) }
                        if (moduleInfo != null) {
                            val provides: MutableList<ModuleProvideNode> = moduleInfo.module.provides ?: run {
                                val provides = mutableListOf<ModuleProvideNode>()
                                moduleInfo.module.provides = provides
                                provides
                            }
                            provides.add(ModuleProvideNode(
                                DEBUG_PROBES_PROVIDER_CLASS_NAME.className2InternalName,
                                listOf(DEBUG_PROBES_PROVIDER_IMPL_CLASS_NAME.className2InternalName)
                            ))
                            val uses: MutableList<String> = moduleInfo.module.uses ?: run {
                                val uses = mutableListOf<String>()
                                moduleInfo.module.uses = uses
                                uses
                            }
                            uses.add(DecoroutinatorRuntimeSettingsProvider::class.jvmName.className2InternalName)
                            moduleInfo.module.addRequiresModule("dev.reformator.stacktracedecoroutinator.runtimesettings")
                            newArtifact.addFile(path, moduleInfo.classBody.inputStream())
                            return true
                        }
                    }
                    newArtifact.addFile(path, reader())
                    return true
                }

                override fun onDirectory(path: ArtifactPath): Boolean {
                    newArtifact.addDirectory(path)
                    return true
                }
            })
            newArtifact.addFile(
                path = DEBUG_PROBES_PROVIDER_IMPL_CLASS_NAME.className2ArtifactPath,
                body = getDebugProbesProviderImplClassBodyStream()
            )
            newArtifact.addFile(
                path = DEBUG_PROBES_PROVIDER_UTILS_CLASS_NAME.className2ArtifactPath,
                body = getDebugProbesProviderUtilsClassBodyStream()
            )
            val metaInfDirPath = listOf("META-INF")
            if (!containsDirectory(metaInfDirPath)) {
                newArtifact.addDirectory(metaInfDirPath)
            }
            val servicesDirPath = metaInfDirPath + "services"
            if (!containsDirectory(servicesDirPath)) {
                newArtifact.addDirectory(servicesDirPath)
            }
            newArtifact.addFile(
                path = servicesDirPath + DEBUG_PROBES_PROVIDER_CLASS_NAME,
                body = DEBUG_PROBES_PROVIDER_IMPL_CLASS_NAME.toByteArray().inputStream()
            )
        }
    } else {
        skipArtifact()
    }
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
            try {
                ZipFile(root).use { zip ->
                    ZipArtifact(zip).processArtifact(
                        skipArtifact = {
                            log.debug { "file artifact [${root.absolutePath}] was skipped" }
                            outputs.file(inputArtifact)
                        },
                        modifyArtifact = { builder ->
                            val newName = run {
                                val suffix = root.name.lastIndexOf('.').let { index ->
                                    if (index == -1) "" else root.name.substring(index)
                                }
                                root.name.removeSuffix(suffix) + "-embed-dep-props" + suffix
                            }
                            val newFile = outputs.file(newName)
                            ZipOutputStream(newFile.outputStream()).use { output ->
                                builder(ZipArtifactBuilder(output))
                            }
                            log.debug { "file artifact [${root.absolutePath}] was transformed to [${newFile.absolutePath}]" }
                        }
                    )
                }
            } catch (e: IOException) {
                log.debug(e) { "Failed to read zip file [${root.absolutePath}]" }
                outputs.file(inputArtifact)
            }
        } else if (root.isDirectory) {
            log.debug { "artifact [${root.absolutePath}] is a directory" }
            DirectoryArtifact(root).processArtifact(
                skipArtifact = {
                    log.debug { "directory artifact [${root.absolutePath}] was skipped" }
                    outputs.dir(inputArtifact)
                },
                modifyArtifact = { builder ->
                    val newName = root.name + "-embed-dep-props"
                    val newRoot = outputs.dir(newName)
                    builder(DirectoryArtifact(newRoot))
                    log.debug { "directory artifact [${root.absolutePath}] was transformed to [${newRoot.absolutePath}]" }
                }
            )
        } else {
            log.debug { "artifact [${root.absolutePath}] does not exist" }
        }
    }
}

private val debugProbesProviderClassBodyBase64: String
    @LoadConstant get() = fail()
private val debugProbesKtClassBodyBase64: String
    @LoadConstant get() = fail()
private val debugProbesProviderImplClassBodyBase64: String
    @LoadConstant get() = fail()
private val debugProbesProviderUtilsClassBodyBase64: String
    @LoadConstant get() = fail()

private fun getDebugProbesKtClassBodyStream() =
    ByteArrayInputStream(Base64.getDecoder().decode(debugProbesKtClassBodyBase64))
private fun getDebugProbesProviderClassBodyStream() =
    ByteArrayInputStream(Base64.getDecoder().decode(debugProbesProviderClassBodyBase64))
private fun getDebugProbesProviderImplClassBodyStream() =
    ByteArrayInputStream(Base64.getDecoder().decode(debugProbesProviderImplClassBodyBase64))
private fun getDebugProbesProviderUtilsClassBodyStream() =
    ByteArrayInputStream(Base64.getDecoder().decode(debugProbesProviderUtilsClassBodyBase64))
