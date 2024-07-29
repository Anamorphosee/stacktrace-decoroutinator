@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.gradleplugin

import dev.reformator.stacktracedecoroutinator.generator.getDebugMetadataInfoFromClassBody
import dev.reformator.stacktracedecoroutinator.generator.loadResource
import dev.reformator.stacktracedecoroutinator.generator.tryTransformForDecoroutinator
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.*
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

private const val JAR_SUFFIX = ".jar"
private const val CLASS_SUFFIX = ".class"
internal const val EXTENSION_NAME = "stacktraceDecoroutinator"

private val stdlibTransformedAttribute = Attribute.of(
    "dev.reformator.stacktracedecoroutinator.gradleplugin.stdlibTransformed",
    Boolean::class.javaObjectType
)

private val pluginProperties = Properties().apply {
    load(
        ByteArrayInputStream(loadResource("dev.reformator.stacktracedecoroutinator.gradleplugin.properties"))
    )
}.mapKeys { (key, _) -> key.toString() }.mapValues { (_, value) -> value.toString() }

open class DecoroutinatorPluginExtension {
    var enabled = true
    var configurations = setOf(
        "runtimeClasspath",
        "testRuntimeClasspath",
        "releaseRuntimeClasspath",
        "releaseUnitTestRuntimeClasspath",
        "debugAndroidTestRuntimeClasspath",
        "debugRuntimeClasspath",
        "debugUnitTestRuntimeClasspath"
    )
    var addRuntimeDependency = true
    var tasks = setOf(
        "compileKotlin",
        "compileTestKotlin",
        "compileDebugKotlin",
        "compileReleaseKotlin",
        "compileDebugAndroidTestSources",
        "compileDebugSources",
        "compileDebugUnitTestSources",
        "compileReleaseSources",
        "compileReleaseUnitTestSources"
    )
}

class DecoroutinatorPlugin: Plugin<Project> {
    override fun apply(target: Project) {
        with (target) {
            val pluginExtension = extensions.create(EXTENSION_NAME, DecoroutinatorPluginExtension::class.java)
            dependencies.attributesSchema.attribute(stdlibTransformedAttribute)
            dependencies.artifactTypes.getByName("jar") {
                it.attributes.attribute(stdlibTransformedAttribute, false)
            }
            dependencies.registerTransform(DecoroutinatorTransformAction::class.java) {
                it.from.attribute(stdlibTransformedAttribute, false)
                it.to.attribute(stdlibTransformedAttribute, true)
            }

            afterEvaluate { _ ->
                if (pluginExtension.enabled) {
                    configurations.all {
                        if (it.name in pluginExtension.configurations) {
                            it.attributes.attribute(stdlibTransformedAttribute, true)
                        }
                    }
                    if (pluginExtension.addRuntimeDependency) {
                        dependencies.add(
                            "runtimeOnly",
                            "dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-runtime:${pluginProperties["version"]}"
                        )
                    }
                    pluginExtension.tasks.forEach { taskName ->
                        tasks.findByName(taskName)?.let { task ->
                            task.doLast { _ ->
                                task.outputs.files.files.forEach { transformClasses(it) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun transformClasses(root: File) {
    root.walk().forEach { file ->
        if (file.isFile && file.name.endsWith(CLASS_SUFFIX)) {
            val transformedBody = file.inputStream().use { classBody ->
                tryTransformForDecoroutinator(
                    className = file.toRelativeString(root).removeSuffix(CLASS_SUFFIX).replace(File.separatorChar, '.'),
                    classBody = classBody,
                    metadataResolver = { className ->
                        val classRelativePath = className.replace('.', File.separatorChar) + CLASS_SUFFIX
                        val classPath = root.resolve(classRelativePath)
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
                file.writeBytes(transformedBody)
            }
        }
    }
}

abstract class DecoroutinatorTransformAction: TransformAction<TransformParameters.None> {
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val jar = inputArtifact.get().asFile

        val needModification = run {
            transformJar(
                jar = jar,
                putNextEntry = { },
                putFileBody = { modified, _ ->
                    if (modified) {
                        return@run true
                    }
                },
                closeEntry = { }
            )
            false
        }

        if (needModification) {
            require(jar.name.endsWith(JAR_SUFFIX, ignoreCase = true)) { "file name must end with .jar!" }
            val newName = jar.name.substring(0, jar.name.length - JAR_SUFFIX.length) + "-decoroutinator.jar"
            JarOutputStream(outputs.file(newName).outputStream()).use { output ->
                transformJar(
                    jar = jar,
                    putNextEntry = { output.putNextEntry(it) },
                    putFileBody = { _, body -> body.copyTo(output) },
                    closeEntry = { output.closeEntry() }
                )
            }
        } else {
            outputs.file(inputArtifact)
        }
    }
}

private inline fun transformJar(
    jar: File,
    putNextEntry: (ZipEntry) -> Unit,
    putFileBody: (modified: Boolean, body: InputStream) -> Unit,
    closeEntry: () -> Unit
) {
    JarFile(jar).use { jarFile ->
        jarFile.entries().asSequence().forEach { entry: ZipEntry ->
            putNextEntry(ZipEntry(entry.name).apply {
                entry.lastModifiedTime?.let { lastModifiedTime = it }
                entry.lastAccessTime?.let { lastAccessTime = it }
                entry.creationTime?.let { creationTime = it }
                method = ZipEntry.DEFLATED
                comment = entry.comment
            })
            if (!entry.isDirectory) {
                var newBody: ByteArray? = null
                if (entry.name.endsWith(CLASS_SUFFIX)) {
                    newBody = jarFile.getInputStream(entry).use { classBody ->
                        tryTransformForDecoroutinator(
                            className = entry.name.substring(0, entry.name.length - CLASS_SUFFIX.length).replace('/', '.'),
                            classBody = classBody,
                            metadataResolver = metadataResolver@{ metadataClassName ->
                                val entryName = metadataClassName.replace('.', '/') + ".class"
                                val classEntry = jarFile.getEntry(entryName) ?: return@metadataResolver null
                                jarFile.getInputStream(classEntry).use {
                                    getDebugMetadataInfoFromClassBody(it)
                                }
                            }
                        )
                    }
                }
                val modified = newBody != null
                (if (modified) ByteArrayInputStream(newBody) else jarFile.getInputStream(entry)).use { body ->
                    putFileBody(modified, body)
                }
            }
            closeEntry()
        }
    }
}
