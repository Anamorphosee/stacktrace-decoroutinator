@file:Suppress("PackageDirectoryMismatch")

import dev.reformator.stacktracedecoroutinator.classtransformer.internal.transformClassBody
import dev.reformator.stacktracedecoroutinator.gradleplugin.addRequiresModule
import dev.reformator.stacktracedecoroutinator.gradleplugin.classBody
import dev.reformator.stacktracedecoroutinator.gradleplugin.tryReadModuleInfo
import dev.reformator.stacktracedecoroutinator.intrinsics.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.intrinsics.PROVIDER_MODULE_NAME
import dev.reformator.stacktracedecoroutinator.provider.internal.internalName
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
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

@Suppress("unused")
class DecoroutinatorTransformBaseContinuationGradlePlugin: Plugin<Project> {
    override fun apply(target: Project) {
        with (target) {
            dependencies.attributesSchema.attribute(decoroutinatorTransformedBaseContinuationAttribute)
            dependencies.artifactTypes.getByName("jar") { artifact ->
                artifact.attributes.attribute(decoroutinatorTransformedBaseContinuationAttribute, false)
            }
            dependencies.registerTransform(DecoroutinatorTransformBaseContinuationAction::class.java) { spec ->
                spec.from.attribute(decoroutinatorTransformedBaseContinuationAttribute, false)
                spec.from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
                spec.to.attribute(decoroutinatorTransformedBaseContinuationAttribute, true)
                spec.to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
            }
        }
    }
}

val decoroutinatorTransformedBaseContinuationAttribute: Attribute<Boolean> = Attribute.of(
    "decoroutinatorTransformedBaseContinuation",
    Boolean::class.javaObjectType
)

abstract class DecoroutinatorTransformBaseContinuationAction: TransformAction<TransformParameters.None> {
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val file = inputArtifact.get().asFile
        if (file.name.startsWith("kotlin-stdlib-") && file.extension == "jar") {
            JarOutputStream(outputs.file("kotlin-stdlib-transformed.jar").outputStream()).use { output ->
                JarFile(file).use { input ->
                    input.entries().asSequence().forEach { entry ->
                        output.putNextEntry(ZipEntry(entry.name).apply {
                            method = ZipEntry.DEFLATED
                        })
                        if (entry.name == BASE_CONTINUATION_CLASS_NAME.internalName + ".class") {
                            output.write(input.getInputStream(entry).use {
                                transformClassBody(
                                    classBody = it,
                                    skipSpecMethods = false,
                                    metadataResolver = { error("no need") }
                                ).updatedBody!!
                            })
                        } else if (entry.name.endsWith("/module-info.class")) {
                            output.write(input.getInputStream(entry).use { input ->
                                val moduleNode = tryReadModuleInfo(input)!!
                                moduleNode.module.addRequiresModule(PROVIDER_MODULE_NAME)
                                moduleNode.classBody
                            })
                        } else if (!entry.isDirectory) {
                            input.getInputStream(entry).use { it.copyTo(output) }
                        }
                        output.closeEntry()
                    }
                }
            }
        } else {
            outputs.file(inputArtifact)
        }
    }
}
