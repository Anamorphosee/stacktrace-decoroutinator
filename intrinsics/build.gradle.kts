import dev.reformator.bytecodeprocessor.plugins.ChangeClassNameProcessor
import dev.reformator.bytecodeprocessor.plugins.LoadConstantProcessor
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("dev.reformator.forcevariantjavaversion")
    id("dev.reformator.bytecodeprocessor")
}

repositories {
    mavenCentral()
}

dependencies {
    //noinspection UseTomlInstead
    compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_9
    targetCompatibility = JavaVersion.VERSION_1_9
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

sourceSets {
    main {
        kotlin.destinationDirectory = java.destinationDirectory
    }
}

bytecodeProcessor {
    processors = listOf(
        ChangeClassNameProcessor,
        LoadConstantProcessor
    )
    skipUpdate = true
}
