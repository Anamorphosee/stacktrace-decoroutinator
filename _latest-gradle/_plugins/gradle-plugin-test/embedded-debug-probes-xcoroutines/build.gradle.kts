import dev.reformator.bytecodeprocessor.plugins.DeleteClassProcessor
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("dev.reformator.bytecodeprocessor")
}

bytecodeProcessor {
    processors = setOf(
        DeleteClassProcessor
    )
}

repositories {
    mavenCentral()
}

dependencies {
    //noinspection UseTomlInstead
    compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")
    compileOnly(project(":runtime-settings"))
    compileOnly(project(":embedded-debug-probes-stdlib"))
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
kotlinSources.srcDirs("../../../../gradle-plugin/embedded-debug-probes-xcoroutines/src/main/kotlin")
resourcesSources.srcDirs("../../../../gradle-plugin/embedded-debug-probes-xcoroutines/src/main/resources")
