import dev.reformator.bytecodeprocessor.plugins.*
import org.gradle.kotlin.dsl.named
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.util.Base64
import kotlin.jvm.java

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
        GetOwnerClassProcessor(),
        ChangeClassNameProcessor(),
        ChangeInvocationsOwnerProcessor,
        SkipInvocationsProcessor,
        MakeStaticProcessor,
        RemoveKotlinStdlibProcessor(includeClassNames = setOf(
            Regex("dev.reformator.stacktracedecoroutinator.generator.internal.GeneratorSpecImpl")
        ))
    )
}

val fillConstantProcessorTask = tasks.register("fillConstantProcessor") {
    val embeddedDebugProbesProject = project(":embedded-debug-probes")
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
        val debugProbesProviderUtilsBody = embeddedDebugProbesCompileKotlinTask.get().destinationDirectory.get()
            .dir("kotlinx").dir("coroutines").dir("debug").dir("internal")
            .file("DecoroutinatorDebugProbesProviderUtilsKt.class").asFile.readBytes()
        val base64Encoder = Base64.getEncoder()
        bytecodeProcessor {
            processors += LoadConstantProcessor(mapOf(
                LoadConstantProcessor.Key(
                    "org.gradle.kotlin.dsl.ApiGradlePluginDecoroutinatorKt",
                    "getProjectVersionIntrinsic"
                ) to LoadConstantProcessor.Value("unspecified"),
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
                ) to LoadConstantProcessor.Value(base64Encoder.encodeToString(debugProbesProviderImplBody)),
                LoadConstantProcessor.Key(
                    "dev.reformator.stacktracedecoroutinator.gradleplugin.DebugProbesEmbedderKt",
                    "getDebugProbesProviderUtilsClassBodyBase64"
                ) to LoadConstantProcessor.Value(base64Encoder.encodeToString(debugProbesProviderUtilsBody))
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
    }
}
