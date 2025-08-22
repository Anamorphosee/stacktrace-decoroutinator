import dev.reformator.bytecodeprocessor.plugins.*
import org.gradle.kotlin.dsl.named
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Base64

buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm.build)
    alias(libs.plugins.gradle.plugin.publish)
    id("dev.reformator.bytecodeprocessor")
}

group = "dev.reformator.gradle-plugin-test"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    //noinspection UseTomlInstead
    compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")
    compileOnly(project(":intrinsics"))

    implementation(project(":runtime-settings"))
    implementation(project(":provider"))
    implementation(libs.kotlin.gradle.plugin.api)
    implementation(libs.asm.utils)
    implementation(libs.kotlin.logging.jvm)
}

bytecodeProcessor {
    dependentProjects = listOf(
        project(":intrinsics"),
        project(":provider"),
        project(":base-continuation-accessor")
    )
    processors = setOf(
        GetOwnerClassProcessor,
        ChangeClassNameProcessor,
        ChangeInvocationsOwnerProcessor,
        SkipInvocationsProcessor,
        MakeStaticProcessor,
        RemoveKotlinStdlibProcessor,
        LoadConstantProcessor
    )
    initContext {
        LoadConstantProcessor.addValues(this, mapOf("version" to "unknown"))
    }
}

val fillConstantProcessorTask = tasks.register("fillConstantProcessor") {
    val embeddedDebugProbesStdlibJarTask =
        project(":embedded-debug-probes-stdlib").tasks.named<Jar>("jar")
    val embeddedDebugProbesXcoroutinesJarTask =
        project(":embedded-debug-probes-xcoroutines").tasks.named<Jar>("jar")
    val baseContinuationAccessorJarTask =
        project(":base-continuation-accessor").tasks.named<Jar>("jar")
    dependsOn(
        embeddedDebugProbesStdlibJarTask,
        embeddedDebugProbesXcoroutinesJarTask,
        baseContinuationAccessorJarTask
    )
    doLast {
        val embeddedDebugProbesStdlibJarBody =
            embeddedDebugProbesStdlibJarTask.get().archiveFile.get().asFile.readBytes()
        val embeddedDebugProbesXcoroutinesJarBody =
            embeddedDebugProbesXcoroutinesJarTask.get().archiveFile.get().asFile.readBytes()
        val baseContinuationAccessorJarBody =
            baseContinuationAccessorJarTask.get().archiveFile.get().asFile.readBytes()
        bytecodeProcessor {
            initContext {
                val base64Encoder = Base64.getEncoder()
                LoadConstantProcessor.addValues(this, mapOf(
                    "embeddedDebugProbesStdlibJarBase64"
                            to base64Encoder.encodeToString(embeddedDebugProbesStdlibJarBody),
                    "embeddedDebugProbesXcoroutinesJarBase64"
                            to base64Encoder.encodeToString(embeddedDebugProbesXcoroutinesJarBody),
                    "baseContinuationAccessorJarBase64"
                            to base64Encoder.encodeToString(baseContinuationAccessorJarBody)
                ))
            }
        }
    }
}

bytecodeProcessorInitTask.dependsOn(fillConstantProcessorTask)

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

val kotlinSources = sourceSets.main.get().kotlin
val resourcesSources = sourceSets.main.get().resources
kotlinSources.srcDirs("../../common/src/main/kotlin")
resourcesSources.srcDirs("../../common/src/main/resources")
kotlinSources.srcDirs("../../generator/src/main/kotlin")
resourcesSources.srcDirs("../../generator/src/main/resources")
kotlinSources.srcDirs("../../gradle-plugin/src/main/kotlin")


gradlePlugin {
    plugins {
        create("decoroutinatorPlugin") {
            id = "dev.reformator.stacktracedecoroutinator"
            implementationClass = "dev.reformator.stacktracedecoroutinator.gradleplugin.DecoroutinatorPlugin"
        }
        create("decoroutinatorAttributePlugin") {
            id = "dev.reformator.stacktracedecoroutinator.attribute"
            implementationClass = "dev.reformator.stacktracedecoroutinator.gradleplugin.DecoroutinatorAttributePlugin"
        }
    }
}
