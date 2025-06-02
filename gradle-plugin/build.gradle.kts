import dev.reformator.bytecodeprocessor.plugins.LoadConstantProcessor
import org.gradle.kotlin.dsl.named
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.util.Base64

plugins {
    kotlin("jvm")
    alias(libs.plugins.dokka)
    alias(libs.plugins.nmcp)
    `maven-publish`
    signing
    alias(libs.plugins.gradle.plugin.publish)
    id("dev.reformator.bytecodeprocessor")
}

repositories {
    mavenCentral()
}

gradlePlugin {
    website = "https://github.com/Anamorphosee/stacktrace-decoroutinator"
    vcsUrl = "https://github.com/Anamorphosee/stacktrace-decoroutinator.git"
    plugins {
        create("decoroutinatorPlugin") {
            id = "dev.reformator.stacktracedecoroutinator"
            implementationClass = "dev.reformator.stacktracedecoroutinator.gradleplugin.DecoroutinatorPlugin"
            displayName = "Stacktrace Decoroutinator Gradle Plugin"
            description = "Gradle plugin for recovering stack trace in exceptions thrown in Kotlin coroutines"
            tags = listOf("kotlin", "coroutines", "debug", "kotlin-coroutines")
        }
    }
}

afterEvaluate {
    tasks.named<Jar>("javadocJar") {
        from(tasks.named("dokkaJavadoc"))
    }
}

dependencies {
    //noinspection UseTomlInstead
    compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")

    compileOnly(libs.kotlinx.coroutines.debug.build)
    implementation(project(":stacktrace-decoroutinator-common"))
    implementation(project(":stacktrace-decoroutinator-generator"))
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.kotlin.gradle.plugin.api)
    implementation(libs.asm.utils)

    runtimeOnly(project(":stacktrace-decoroutinator-mh-invoker"))

    testImplementation(kotlin("test"))
}

val fillConstantProcessorTask = tasks.register("fillConstantProcessor") {
    val embeddedDebugProbesProject = project(":gradle-plugin:embedded-debug-probes")
    val embeddedDebugProbesCompileKotlinTask =
        embeddedDebugProbesProject.tasks.named<KotlinJvmCompile>("compileKotlin")
    dependsOn(embeddedDebugProbesCompileKotlinTask)
    doLast {
        val debugProbesProviderBody = embeddedDebugProbesCompileKotlinTask.get().destinationDirectory.get()
            .dir("kotlin").dir("coroutines").dir("jvm").dir("internal")
            .file("DecoroutinatorDebugProbesProvider.class").asFile.readBytes()
        val debugProbesBody = embeddedDebugProbesCompileKotlinTask.get().destinationDirectory.get()
            .dir("kotlin").dir("coroutines").dir("jvm").dir("internal")
            .file("DebugProbesKt.class").asFile.readBytes()
        val debugProbesProviderImplBody = embeddedDebugProbesCompileKotlinTask.get().destinationDirectory.get()
            .dir("kotlinx").dir("coroutines").dir("debug").dir("internal")
            .file("DecoroutinatorDebugProbesProviderImpl.class").asFile.readBytes()
        val base64Encoder = Base64.getEncoder()
        bytecodeProcessor {
            processors += LoadConstantProcessor(mapOf(
                LoadConstantProcessor.Key(
                    "org.gradle.kotlin.dsl.ApiGradlePluginDecoroutinatorKt",
                    "getProjectVersionIntrinsic"
                ) to LoadConstantProcessor.Value(project.version),
                LoadConstantProcessor.Key(
                    "dev.reformator.stacktracedecoroutinator.gradleplugin.DebugProbesEmbedderKt",
                    "getDebugProbesKtClassBodyBase64"
                ) to LoadConstantProcessor.Value(base64Encoder.encodeToString(debugProbesBody)),
                LoadConstantProcessor.Key(
                    "dev.reformator.stacktracedecoroutinator.gradleplugin.DebugProbesEmbedderKt",
                    "getDebugProbesProviderClassBodyBase64"
                ) to LoadConstantProcessor.Value(base64Encoder.encodeToString(debugProbesProviderBody)),
                LoadConstantProcessor.Key(
                    "dev.reformator.stacktracedecoroutinator.gradleplugin.DebugProbesEmbedderKt",
                    "getDebugProbesProviderImplClassBodyBase64"
                ) to LoadConstantProcessor.Value(base64Encoder.encodeToString(debugProbesProviderImplBody))
            ))
        }
    }
}

tasks.withType(KotlinJvmCompile::class.java) {
    dependsOn(fillConstantProcessorTask)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

tasks.test {
    useJUnitPlatform()
    dependsOn(project(":gradle-plugin:tests-gp").tasks.test)
    dependsOn(project(":gradle-plugin:jdk8-tests-gp").tasks.test)
}

val mavenPublicationName = "maven"

publishing {
    publications {
        create<MavenPublication>(mavenPublicationName) {
            artifactId = "dev.reformator.stacktracedecoroutinator.gradle.plugin"
            from(components["java"])
            pom {
                name.set("Stacktrace-decoroutinator Gradle plugin.")
                description.set("Library for recovering stack trace in exceptions thrown in Kotlin coroutines.")
                url.set("https://stacktracedecoroutinator.reformator.dev")
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
}

signing {
    useGpgCmd()
    sign(publishing.publications[mavenPublicationName])
}

nmcp {
    publish(mavenPublicationName) {}
}
