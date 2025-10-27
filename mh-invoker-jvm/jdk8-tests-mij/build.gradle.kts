import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

val transformedAttribute = Attribute.of("transformed", Boolean::class.javaObjectType)

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
                        if (entry.name == BASE_CONTINUATION_CLASS_NAME.internalName + ".class") {
                            output.write(input.getInputStream(entry).use {
                                transformClassBody(
                                    classBody = it,
                                    skipSpecMethods = false,
                                    metadataResolver = { error("no need") }
                                ).updatedBody!!
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


    testRuntimeOnly(project(":stacktrace-decoroutinator-generator-jvm"))
    testRuntimeOnly(project(":stacktrace-decoroutinator-mh-invoker-jvm"))

    testImplementation(kotlin("test"))
    testImplementation(project(":test-utils"))
    testImplementation(project(":test-utils-jvm"))
    testRuntimeOnly(project(":test-utils:base-continuation-accessor-stub"))
}

afterEvaluate {
    configurations.testRuntimeClasspath.get().attributes.attribute(transformedAttribute, true)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}
