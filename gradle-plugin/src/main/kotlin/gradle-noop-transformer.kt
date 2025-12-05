@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.gradleplugin

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

private val log = KotlinLogging.logger { }

@CacheableTransform
abstract class DecoroutinatorNoopTransformAction: TransformAction<TransformParameters.None> {
    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val root = inputArtifact.get().asFile
        log.debug { "noop transformation for [${root.absolutePath}]" }
        if (root.isFile) {
            log.debug { "artifact [${root.absolutePath}] is a file" }
            outputs.file(inputArtifact)
        } else if (root.isDirectory) {
            log.debug { "artifact [${root.absolutePath}] is a directory" }
            outputs.dir(inputArtifact)
        } else {
            log.debug { "artifact [${root.absolutePath}] does not exist" }
        }
    }
}
