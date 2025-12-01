import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.reformator.bytecodeprocessor.plugins.*



plugins {
    alias(libs.plugins.kotlin.jvm.latest)
    id("dev.reformator.bytecodeprocessor")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(files("../../../test-utils/retrace-repack/build/libs/").asFileTree)
    implementation(files("../../../common/build/libs/").asFileTree)
    implementation(kotlin("test"))
    implementation(libs.junit5.api)
    implementation(libs.junit4)
    implementation(libs.kotlinx.coroutines.jdk8.latest)
    implementation(libs.kotlin.logging.jvm)

    //noinspection UseTomlInstead
    compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")

    runtimeOnly(libs.ktor.io.jvm)
    runtimeOnly(libs.logback.classic)
}

bytecodeProcessor {
    processors = setOf(
        GetCurrentFileNameProcessor,
        GetOwnerClassProcessor,
        LoadConstantProcessor
    )
}

val fillConstantProcessorTask = tasks.register("fillConstantProcessor") {
    val customLoaderProject = project(":custom-loader")
    val customLoaderJarTask = customLoaderProject.tasks.named<ShadowJar>("shadowJar")
    dependsOn(customLoaderJarTask)
    doLast {
        val customLoaderJarUri = customLoaderJarTask.get().archiveFile.get().asFile.toURI().toString()
        bytecodeProcessor {
            initContext {
                LoadConstantProcessor.addValues(
                    context = this,
                    valuesByKeys = mapOf("customLoaderJarUri" to customLoaderJarUri)
                )
            }
        }
    }
}

bytecodeProcessorInitTask.dependsOn(fillConstantProcessorTask)

val kotlinSources = sourceSets.main.get().kotlin
kotlinSources.srcDirs("../../../test-utils/src/main/kotlin")
kotlinSources.srcDirs("../../../test-utils-jvm/src/main/kotlin")
