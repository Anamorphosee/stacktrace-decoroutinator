import dev.reformator.bytecodeprocessor.plugins.*
import org.jetbrains.dokka.gradle.AbstractDokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.util.Base64
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

plugins {
    alias(libs.plugins.android.library)
    kotlin("android")
    alias(libs.plugins.dokka)
    alias(libs.plugins.nmcp)
    `maven-publish`
    signing
    id("dev.reformator.bytecodeprocessor")
}

repositories {
    mavenCentral()
    google()
}

android {
    namespace = "dev.reformator.stacktracedecoroutinator.mhinvokerandroid"
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

    //noinspection UseTomlInstead
    compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")

    implementation(project(":stacktrace-decoroutinator-common"))

    androidTestRuntimeOnly(project(":stacktrace-decoroutinator-generator-android"))
    androidTestRuntimeOnly(project(":test-utils"))
    androidTestRuntimeOnly(libs.androidx.test.runner)
    androidTestRuntimeOnly(project(":test-utils:base-continuation-accessor-reflect-stub"))
}

bytecodeProcessor {
    dependentProjects = listOf(project(":stacktrace-decoroutinator-common"))
    processors = listOf(
        ChangeClassNameProcessor,
        LoadConstantProcessor
    )
}

val fillConstantProcessorTask = tasks.register("fillConstantProcessor") {
    val mhInvokerProject = project(":stacktrace-decoroutinator-mh-invoker")
    val mhInvokerCompileKotlinTask = mhInvokerProject.tasks.named<KotlinJvmCompile>("compileKotlin")
    dependsOn(mhInvokerCompileKotlinTask)
    doLast {
        val tmpDir = temporaryDir
        providers.exec {
            setCommandLine((
                sequenceOf(
                    "${android.sdkDirectory}/build-tools/${android.buildToolsVersion}/d8",
                    "--min-api", "26",
                    "--output", tmpDir.absolutePath
                ) + mhInvokerCompileKotlinTask.get()
                    .destinationDirectory.get().asFile.walk()
                    .filter { it.isFile && it.name.endsWith(".class") && it.name != "module-info.class" }
                    .map { it.absolutePath }
            ).asIterable())
        }.result.get().rethrowFailure()
        bytecodeProcessor {
            initContext {
                LoadConstantProcessor.addValues(this, mapOf(
                    "regularMethodHandleDexBase64" to
                            Base64.getEncoder().encodeToString(tmpDir.resolve("classes.dex").readBytes())
                ))
            }
        }
    }
}

bytecodeProcessorInitTask.dependsOn(fillConstantProcessorTask)

afterEvaluate {
    configurations["debugAndroidTestRuntimeClasspath"].attributes.attribute(transformedAttribute, true)
}

val dokkaJavadocsJar = tasks.register<Jar>("dokkaJavadocsJar") {
    val dokkaJavadocTask = tasks.named<AbstractDokkaTask>("dokkaJavadoc").get()
    dependsOn(dokkaJavadocTask)
    archiveClassifier.set("javadoc")
    from(dokkaJavadocTask.outputDirectory)
}

val mavenPublicationName = "maven"

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>(mavenPublicationName) {
                from(components["release"])
                artifact(dokkaJavadocsJar)
                pom {
                    name.set("Stacktrace-decoroutinator Android runtime MethodHandle invoker.")
                    description.set("Android library for recovering stack trace in exceptions thrown in Kotlin coroutines.")
                    url.set("https://github.com/Anamorphosee/stacktrace-decoroutinator")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            name.set("Denis Berestinskii")
                            email.set("berestinsky@gmail.com")
                            url.set("https://github.com/Anamorphosee")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/Anamorphosee/stacktrace-decoroutinator.git")
                        developerConnection.set("scm:git:ssh://github.com:Anamorphosee/stacktrace-decoroutinator.git")
                        url.set("http://github.com/Anamorphosee/stacktrace-decoroutinator/tree/master")
                    }
                }
            }
        }
        repositories {
            maven {
                name = "snapshot"
                url = uri("https://central.sonatype.com/repository/maven-snapshots/")
                credentials {
                    username = properties["sonatype.username"] as String?
                    password = properties["sonatype.password"] as String?
                }
            }
        }
    }

    signing {
        useGpgCmd()
        sign(publishing.publications[mavenPublicationName])
    }

    nmcp {
        publish(mavenPublicationName) {}
    }
}
