import dev.reformator.bytecodeprocessor.plugins.*
import org.jetbrains.dokka.gradle.AbstractDokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.util.Base64

plugins {
    alias(libs.plugins.android.library)
    kotlin("android")
    alias(libs.plugins.dokka)
    alias(libs.plugins.nmcp)
    `maven-publish`
    signing
    id("dev.reformator.bytecodeprocessor")
    id("decoroutinatorTransformBaseContinuation")
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

dependencies {
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
    configurations["debugAndroidTestRuntimeClasspath"].attributes.attribute(decoroutinatorTransformedBaseContinuationAttribute, true)
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
