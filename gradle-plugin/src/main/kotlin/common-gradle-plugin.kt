@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.gradleplugin

import dev.reformator.stacktracedecoroutinator.generator.getDebugMetadataInfoFromClassBody
import dev.reformator.stacktracedecoroutinator.generator.loadResource
import dev.reformator.stacktracedecoroutinator.generator.tryTransformForDecoroutinator
import mu.KotlinLogging
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

val decoroutinatorTransformedAttribute: Attribute<Boolean> = Attribute.of(
    "dev.reformator.stacktracedecoroutinator.transformed",
    Boolean::class.javaObjectType
)

open class DecoroutinatorPluginExtension(project: Project) {
    var enabled = true
    var addGeneratorDependency = false

    var _artifactTypes = setOf(
        ArtifactTypeDefinition.JAR_TYPE,
        ArtifactTypeDefinition.JVM_CLASS_DIRECTORY,
        ArtifactTypeDefinition.ZIP_TYPE,
        "aar",
    )
    var _addRuntimeDependency = true
    var _runtimeDependencyConfigName = "runtimeOnly"
    var _configurationsInclude = setOf(
        "runtimeClasspath",
        ".+RuntimeClasspath"
    )
    var _configurationsExclude = setOf<String>()
    var _tasksInclude = setOf("compile.*Kotlin")
    var _tasksExclude = setOf<String>()
    var _isAndroid = project.pluginManager.hasPlugin("com.android.base")
}

class DecoroutinatorPlugin: Plugin<Project> {
    override fun apply(target: Project) {
        log.debug { "applying Decoroutinator plugin to ${target.name}" }
        with (target) {
            val pluginExtension = extensions.create(EXTENSION_NAME, DecoroutinatorPluginExtension::class.java, target)
            dependencies.attributesSchema.attribute(decoroutinatorTransformedAttribute)

            afterEvaluate { _ ->
                if (pluginExtension.enabled) {
                    log.debug { "registering DecoroutinatorArtifactTransfomer for types [${pluginExtension._artifactTypes}]" }
                    pluginExtension._artifactTypes.forEach { artifactType ->
                        dependencies.registerTransform(DecoroutinatorTransformAction::class.java) {
                            it.from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, artifactType)
                            it.from.attribute(decoroutinatorTransformedAttribute, false)
                            it.to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, artifactType)
                            it.to.attribute(decoroutinatorTransformedAttribute, true)
                        }
                        dependencies.artifactTypes.maybeCreate(artifactType).attributes.attribute(decoroutinatorTransformedAttribute, false)
                    }

                    if (pluginExtension._addRuntimeDependency) {
                        dependencies.add(
                            pluginExtension._runtimeDependencyConfigName,
                            "dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-runtime:${pluginProperties["version"]}"
                        )
                    } else {
                        log.debug { "Skipped runtime dependency" }
                    }
                    if (pluginExtension.addGeneratorDependency) {
                        val dependency = if (pluginExtension._isAndroid) {
                            log.debug { "add generator dependency for Android" }
                            "dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-generator-android:${pluginProperties["version"]}"
                        } else {
                            log.debug { "add generator dependency for JVM" }
                            "dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-generator:${pluginProperties["version"]}"
                        }
                        dependencies.add(pluginExtension._runtimeDependencyConfigName, dependency)
                    }

                    run {
                        val includes = pluginExtension._configurationsInclude.map { Regex(it) }
                        val excludes = pluginExtension._configurationsExclude.map { Regex(it) }
                        configurations.all { config ->
                            if (includes.any { it.matches(config.name) } && excludes.all { !it.matches(config.name) }) {
                                log.debug { "setting decoroutinatorTransformedAttribute for configuration [${config.name}]" }
                                config.attributes.attribute(decoroutinatorTransformedAttribute, true)
                            }
                        }
                    }

                    run {
                        val includes = pluginExtension._tasksInclude.map { Regex(it) }
                        val excludes = pluginExtension._tasksExclude.map { Regex(it) }
                        tasks.all { task ->
                            if (includes.any { it.matches(task.name) } && excludes.all { !it.matches(task.name) }) {
                                log.debug { "setting transform classes action for task [${task.name}]" }
                                task.doLast {
                                    task.outputs.files.files.forEach { classes ->
                                        if (classes.isDirectory) {
                                            transformClassesDirInPlace(classes)
                                        } else {
                                            log.debug { "skipping in-place transformation for artifact [${classes.absolutePath}] as it is not a directory" }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val setTransformedAttributeAction = Action<Project> { project ->
                        project.configurations.forEach { conf ->
                            conf.outgoing.variants.forEach { variant ->
                                if (variant.artifacts.any { it.type in pluginExtension._artifactTypes }) {
                                    log.debug { "unsetting decoroutinatorTransformedAttribute for outgoing variant [${variant.name}] of cofiguarion [${conf.name}]" }
                                    variant.attributes.attribute(decoroutinatorTransformedAttribute, false)
                                }
                            }
                        }
                    }
                    rootProject.allprojects { project ->
                        if (project.state.executed) {
                            setTransformedAttributeAction.execute(project)
                        } else {
                            project.afterEvaluate(setTransformedAttributeAction)
                        }
                    }
                } else {
                    log.debug { "Decoroutinator plugin is disabled" }
                }
            }
        }
    }
}

abstract class DecoroutinatorTransformAction: TransformAction<TransformParameters.None> {
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
            outputs.dir("empty")
        }
    }
}

internal const val EXTENSION_NAME = "stacktraceDecoroutinator"
private const val CLASS_EXTENSION = "class"
private val log = KotlinLogging.logger { }

private val pluginProperties = Properties().apply {
    load(
        ByteArrayInputStream(loadResource("dev.reformator.stacktracedecoroutinator.gradleplugin.properties"))
    )
}.mapKeys { (key, _) -> key.toString() }.mapValues { (_, value) -> value.toString() }

private inline fun transformZip(
    zip: File,
    putNextEntry: (ZipEntry) -> Unit,
    putFileBody: (modified: Boolean, body: InputStream) -> Unit,
    closeEntry: () -> Unit
) {
    ZipFile(zip).use { input ->
        input.entries().asSequence().forEach { entry: ZipEntry ->
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
                    newBody = input.getInputStream(entry).use { classBody ->
                        tryTransformForDecoroutinator(
                            className = entry.name.removeSuffix(".$CLASS_EXTENSION").replace('/', '.'),
                            classBody = classBody,
                            metadataResolver = metadataResolver@{ metadataClassName ->
                                val entryName = metadataClassName.replace('.', '/') + ".$CLASS_EXTENSION"
                                val classEntry = input.getEntry(entryName) ?: return@metadataResolver null
                                input.getInputStream(classEntry).use {
                                    getDebugMetadataInfoFromClassBody(it)
                                }
                            }
                        )
                    }
                }
                val modified = newBody != null
                (if (modified) ByteArrayInputStream(newBody) else input.getInputStream(entry)).use { body ->
                    putFileBody(modified, body)
                }
            }
            closeEntry()
        }
    }
}

private inline fun transformClassesDir(
    root: File,
    onDirectory: (relativePath: File) -> Unit,
    onFile: (relativePath: File, content: InputStream, modified: Boolean) -> Unit
) {
    root.walk().forEach { file ->
        val relativePath = file.relativeTo(root)
        if (file.isFile && file.isClass) {
            val transformedBody = file.inputStream().use { classBody ->
                tryTransformForDecoroutinator(
                    className = relativePath.path.removeSuffix(".$CLASS_EXTENSION").replace(File.separatorChar, '.'),
                    classBody = classBody,
                    metadataResolver = { metadataClassName ->
                        val metadataClassRelativePath = metadataClassName.replace('.', File.separatorChar) + ".$CLASS_EXTENSION"
                        val classPath = root.resolve(metadataClassRelativePath)
                        if (classPath.isFile) {
                            classPath.inputStream().use {
                                getDebugMetadataInfoFromClassBody(it)
                            }
                        } else {
                            null
                        }
                    }
                )
            }
            if (transformedBody != null) {
                onFile(relativePath, transformedBody.inputStream(), true)
                return@forEach
            }
        }
        if (file.isFile) {
            file.inputStream().use { input ->
                onFile(relativePath, input, false)
            }
        } else {
            onDirectory(relativePath)
        }
    }
}

private fun transformClassesDirInPlace(dir: File) {
    log.debug { "performing in-place transformation of a classes directory [${dir.absolutePath}]" }
    transformClassesDir(
        root = dir,
        onDirectory = { },
        onFile = { relativePath, content, modified ->
            if (modified) {
                val file = dir.resolve(relativePath)
                log.debug { "class file [${file.absolutePath}] was transformed" }
                file.outputStream().use { output ->
                    content.copyTo(output)
                }
            }
        }
    )
}

private val String.extension: String
    get() = substringAfterLast('.', "")

private val String.isClass: Boolean
    get() = extension == CLASS_EXTENSION

private val File.isClass: Boolean
    get() = name.isClass
