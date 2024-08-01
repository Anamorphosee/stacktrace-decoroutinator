import dev.reformator.stacktracedecoroutinator.generator.loadDecoroutinatorBaseContinuationClassBody
import dev.reformator.stacktracedecoroutinator.runtime.BASE_CONTINUATION_CLASS_NAME
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(project(":test-utils"))
    testImplementation(kotlin("test"))

    testRuntimeOnly(project(":stacktrace-decoroutinator-generator"))
}

tasks.test {
    useJUnitPlatform()
}

val transformedAttribute = Attribute.of(
    "transformed",
    Boolean::class.javaObjectType
)

dependencies {
    attributesSchema.attribute(transformedAttribute)
    artifactTypes.getByName("jar", object: Action<ArtifactTypeDefinition> {
        override fun execute(t: ArtifactTypeDefinition) {
            t.attributes.attribute(transformedAttribute, false)
        }
    })
    registerTransform(Transform::class.java, object: Action<TransformSpec<TransformParameters.None>> {
        override fun execute(t: TransformSpec<TransformParameters.None>) {
            t.from.attribute(transformedAttribute, false)
            t.to.attribute(transformedAttribute, true)
        }
    })
}

afterEvaluate {
    configurations.testRuntimeClasspath.get().attributes.attribute(transformedAttribute, true)
}


abstract class Transform: TransformAction<TransformParameters.None> {
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
                        if (entry.name == BASE_CONTINUATION_CLASS_NAME.replace('.', '/') + ".class") {
                            output.write(loadDecoroutinatorBaseContinuationClassBody())
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
