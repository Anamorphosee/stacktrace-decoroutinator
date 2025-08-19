import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.reformator.bytecodeprocessor.plugins.*

buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    dependencies {
        classpath(libs.shadow.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm.latest)
    id("dev.reformator.stacktracedecoroutinator")
    id("dev.reformator.bytecodeprocessor")
}

stacktraceDecoroutinator {
    regularDependencyConfigurations.include = emptySet()
    androidDependencyConfigurations.include = emptySet()
    jvmDependencyConfigurations.include = emptySet()
    addJvmRuntimeDependency = false
    useTransformedClassesForCompilation = true
    embeddedDebugProbesConfigurations.include = setOf("runtimeClasspath", "testRuntimeClasspath")
}

repositories {
    mavenCentral()
}

dependencies {
    runtimeOnly(files("../../provider/build/libs/").asFileTree)
    implementation(files("../../common/build/libs/").asFileTree)
    implementation(files("../../mh-invoker/build/libs/").asFileTree)
    implementation(files("../../generator/build/libs/").asFileTree)
    implementation(files("../../runtime-settings/build/libs/").asFileTree)
    runtimeOnly(libs.asm.utils)

    //noinspection UseTomlInstead
    testCompileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")

    testImplementation(kotlin("test"))
    testImplementation(libs.junit5.api)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.jdk8.latest)
    testImplementation(libs.kotlin.logging.jvm)
    testImplementation(libs.kotlinx.coroutines.debug.latest)

    testRuntimeOnly(libs.ktor.io.jvm)
    testRuntimeOnly(libs.logback.classic)
}

bytecodeProcessor {
    processors = setOf(
        GetCurrentFileNameProcessor,
        GetOwnerClassProcessor
    )
}

val fillConstantProcessorTask = tasks.register("fillConstantProcessor") {
    val customLoaderProject = project(":custom-loader")
    val customLoaderJarTask = customLoaderProject.tasks.named<ShadowJar>("shadowJar")
    dependsOn(customLoaderJarTask)
    doLast {
        //val customLoaderJarUri = customLoaderJarTask.get().archiveFile.get().asFile.toURI().toString()
        bytecodeProcessor {
            processors += LoadConstantProcessor
        }
    }
}

bytecodeProcessorInitTask.dependsOn(fillConstantProcessorTask)

tasks.test {
    useJUnitPlatform()
}

val kotlinTestSources = sourceSets.test.get().kotlin
kotlinTestSources.srcDirs("../../test-utils/src/main/kotlin")
kotlinTestSources.srcDirs("../../test-utils-jvm/src/main/kotlin")