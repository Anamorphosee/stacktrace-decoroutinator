import dev.reformator.bytecodeprocessor.plugins.ChangeClassNameProcessor
import dev.reformator.bytecodeprocessor.plugins.LoadConstantProcessor
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("dev.reformator.bytecodeprocessor")
}

bytecodeProcessor {
    dependentProjects = listOf(
        project(":intrinsics")
    )
    processors = listOf(
        LoadConstantProcessor,
        ChangeClassNameProcessor
    )
}

repositories {
    mavenCentral()
}

dependencies {
    //noinspection UseTomlInstead
    compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")
    compileOnly(project(":provider"))
    compileOnly(project(":intrinsics"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
        freeCompilerArgs.add("-Xallow-kotlin-package")
    }
}

val kotlinSources = sourceSets.main.get().kotlin
val resourcesSources = sourceSets.main.get().resources
kotlinSources.srcDirs("../../../../gradle-plugin/base-continuation-accessor/src/main/kotlin")
resourcesSources.srcDirs("../../../../gradle-plugin/base-continuation-accessor/src/main/resources")
