@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.gradleplugin

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private val log = KotlinLogging.logger { }

private val classesJarPath = listOf("classes.jar")

@CacheableTransform
abstract class DecoroutinatorAarTransformAction: TransformAction<DecoroutinatorTransformAction.Parameters> {
    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val root = inputArtifact.get().asFile
        log.debug { "trying transform artifact [${root.absolutePath}]" }

        if (!root.exists()) {
            log.debug { "artifact [${root.absolutePath}] does not exist" }
            return
        }

        if (!root.isFile) {
            log.warn { "artifact [${root.absolutePath}] is not a file" }
            outputs.dir(inputArtifact)
            return
        }
        log.debug { "artifact [${root.absolutePath}] is a file" }

        val inputZipFile = try {
            ZipFileArtifact(ZipFile(root))
        } catch (e: IOException) {
            log.warn(e) { "Failed to read artifact [${root.absolutePath}]. It will be skipped." }
            outputs.file(inputArtifact)
            return
        }

        val classesJarReaderProducer = inputZipFile.getFileReader(classesJarPath) ?: run {
            log.warn { "$classesJarPath is not found in artifact [${root.absolutePath}]. It will be skipped." }
            outputs.file(inputArtifact)
            return
        }

        val classesJarArtifact = ZipArtifact { ZipInputStream(classesJarReaderProducer()) }

        val needModification = try {
            var needModification = false
            classesJarArtifact.transform(
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
            log.warn(e) { "error reading artifact [${root.absolutePath}]. It will be skipped." }
            false
        }

        if (!needModification) {
            log.debug { "file artifact [${root.absolutePath}] was skipped" }
            outputs.file(inputArtifact)
            return
        }

        val transformedClassesJarBuffer = ByteArrayOutputStream()
        run {
            ZipOutputStream(transformedClassesJarBuffer).use { output ->
                val transformedClassesArtifact = ZipArtifactBuilder(output)
                classesJarArtifact.transform(
                    skipSpecMethods = parameters.skipSpecMethods.get(),
                    onFile = { _, path, body ->
                        transformedClassesArtifact.addFile(path, body)
                        true
                    },
                    onDirectory = { transformedClassesArtifact.addDirectory(it) }
                )
            }
        }

        val newFile = run {
            val suffix = root.name.lastIndexOf('.').let { index ->
                if (index == -1) "" else root.name.substring(index)
            }
            val newName = root.name.removeSuffix(suffix) + "-decoroutinator" + suffix
            outputs.file(newName)
        }

        ZipOutputStream(newFile.outputStream()).use { output ->
            val outputArtifact = ZipArtifactBuilder(output)
            inputZipFile.walk(object: ArtifactWalker {
                override fun onFile(
                    path: ArtifactPath,
                    reader: () -> InputStream
                ): Boolean {
                    if (path == classesJarPath) {
                        transformedClassesJarBuffer.toByteArray().inputStream().use { body ->
                            outputArtifact.addFile(path, body)
                        }
                    } else {
                        reader().use { body ->
                            outputArtifact.addFile(path, body)
                        }
                    }
                    return true
                }

                override fun onDirectory(path: ArtifactPath): Boolean {
                    outputArtifact.addDirectory(path)
                    return true
                }
            })
        }
    }
}
