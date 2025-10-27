import com.android.build.gradle.internal.tasks.factory.dependsOn
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

plugins {
    alias(libs.plugins.android.library)
    kotlin("android")
}

repositories {
    mavenCentral()
    google()
}

android {
    namespace = "dev.reformator.stacktracedecoroutinator.mhinvokerandroid.legacytests"
    compileSdk = 35
    defaultConfig {
        minSdk = 14
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }
    packaging {
        resources.pickFirsts.add("META-INF/*")
    }
    kotlin {
        jvmToolchain(8)
    }
}

val transformedAttribute = Attribute.of(
    "transformed",
    Boolean::class.javaObjectType
)

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
            t.from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
            t.to.attribute(transformedAttribute, true)
            t.to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
        }
    })

    implementation(project(":stacktrace-decoroutinator-common"))
    runtimeOnly(project(":stacktrace-decoroutinator-generator-android"))
    runtimeOnly(project(":stacktrace-decoroutinator-mh-invoker-android"))

    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.jdk8.build)
    androidTestImplementation(libs.junit5.api)
    androidTestRuntimeOnly(libs.androidx.test.runner)
    androidTestRuntimeOnly(project(":test-utils:base-continuation-accessor-reflect-stub"))
}

afterEvaluate {
    configurations["debugRuntimeClasspath"].attributes.attribute(transformedAttribute, true)
    configurations["releaseRuntimeClasspath"].attributes.attribute(transformedAttribute, true)
    configurations["debugAndroidTestRuntimeClasspath"].attributes.attribute(transformedAttribute, true)
}

afterEvaluate {
    tasks.register("legacyAndroidTest").dependsOn(tasks.named("connectedAndroidTest"))
}
