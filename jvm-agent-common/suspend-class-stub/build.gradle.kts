import dev.reformator.bytecodeprocessor.plugins.LoadConstantProcessor
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("dev.reformator.bytecodeprocessor")
}

repositories {
    mavenCentral()
}

dependencies {
    //noinspection UseTomlInstead
    compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")
}

bytecodeProcessor {
    processors = listOf(LoadConstantProcessor)
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-parameters"))
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}
