import dev.reformator.bytecodeprocessor.plugins.*
import org.gradle.kotlin.dsl.named
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
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

group = "dev.reformator.decoroutinatortest"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    //noinspection UseTomlInstead
    compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")

    implementation(libs.kotlin.gradle.plugin.api)
    implementation(libs.asm.utils)
    implementation(libs.kotlin.logging.jvm)
}

bytecodeProcessor {
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
    val embeddedProject = project(":embedded")
    val embeddedCompileKotlinTask = embeddedProject.tasks.named<KotlinJvmCompile>("compileKotlin")
    dependsOn(embeddedCompileKotlinTask)
    doLast {
        val debugProbesProviderBody = embeddedCompileKotlinTask.get().destinationDirectory.get()
            .dir("kotlin").dir("coroutines").dir("jvm").dir("internal")
            .file("DecoroutinatorDebugProbesProvider.class").asFile.readBytes()
        val debugProbesBody = embeddedCompileKotlinTask.get().destinationDirectory.get()
            .dir("kotlin").dir("coroutines").dir("jvm").dir("internal")
            .file("DebugProbesKt.class").asFile.readBytes()
        val debugProbesProviderImplBody = embeddedCompileKotlinTask.get().destinationDirectory.get()
            .dir("kotlinx").dir("coroutines").dir("debug").dir("internal")
            .file("DecoroutinatorDebugProbesProviderImpl.class").asFile.readBytes()
        val debugProbesProviderUtilsBody = embeddedCompileKotlinTask.get().destinationDirectory.get()
            .dir("kotlinx").dir("coroutines").dir("debug").dir("internal")
            .file("DecoroutinatorDebugProbesProviderUtilsKt.class").asFile.readBytes()
        val regularAccessorBody = embeddedCompileKotlinTask.get().destinationDirectory.get().dir("kotlin")
            .dir("coroutines").dir("jvm").dir("internal")
            .file("DecoroutinatorBaseContinuationAccessorImpl.class").asFile.readBytes()
        val base64Encoder = Base64.getEncoder()
        bytecodeProcessor {
            initContext {
                LoadConstantProcessor.addValues(this, mapOf(
                    "debugProbesKtClassBodyBase64" to base64Encoder.encodeToString(debugProbesBody),
                    "debugProbesProviderClassBodyBase64" to base64Encoder.encodeToString(debugProbesProviderBody),
                    "debugProbesProviderImplClassBodyBase64" to base64Encoder.encodeToString(debugProbesProviderImplBody),
                    "debugProbesProviderUtilsClassBodyBase64" to base64Encoder.encodeToString(debugProbesProviderUtilsBody),
                    "baseContinuationAccessorImplBodyBase64" to base64Encoder.encodeToString(regularAccessorBody)
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
kotlinSources.srcDirs("../../provider/src/main/kotlin")
kotlinSources.srcDirs("../../intrinsics/src/main/kotlin")
kotlinSources.srcDirs("../../runtime-settings/src/main/kotlin")
kotlinSources.srcDirs("../../common/src/main/kotlin")
resourcesSources.srcDirs("../../common/src/main/resources")
kotlinSources.srcDirs("../../mh-invoker/src/main/kotlin")
resourcesSources.srcDirs("../../mh-invoker/src/main/resources")
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
