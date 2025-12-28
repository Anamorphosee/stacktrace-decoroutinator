@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.gradleplugin

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlin.random.Random

private val log = KotlinLogging.logger { }

private val classesJarPath = listOf("classes.jar")

@CacheableTransform
abstract class DecoroutinatorAarTransformAction: TransformAction<DecoroutinatorTransformAction.Parameters> {
    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputArtifact: Provider<FileSystemLocation>

    @get:Inject
    abstract val objects: ObjectFactory

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

        val tempClassesJar = createTempClasserJarFile()
        try {
            classesJarReaderProducer().use { input ->
                tempClassesJar.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val classesJarArtifact = ZipFileArtifact(ZipFile(tempClassesJar))

            val needModification = try {
                classesJarArtifact.doesNeedTransformation(parameters.skipSpecMethods.get())
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
                    classesJarArtifact.transformTo(
                        skipSpecMethods = parameters.skipSpecMethods.get(),
                        builder = transformedClassesArtifact
                    )
                }
            }

            val newFile = outputs.file(root.name.addVariant("decoroutinator"))
            ZipOutputStream(newFile.outputStream()).use { output ->
                val outputArtifact = ZipArtifactBuilder(output)
                inputZipFile.walk(object : ArtifactWalker {
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
        } finally {
            tempClassesJar.delete()
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun createTempClasserJarFile(): File {
        abstract class Service {
            @get:Inject
            abstract val tempFileProvider: TemporaryFileProvider
        }
        val fileName = "classes-${Random.nextBytes(10).toHexString()}.jar"
        val result = objects.newInstance(Service::class.java).tempFileProvider.newTemporaryFile(fileName)
        result.parentFile.mkdirs()
        return result
    }
}
