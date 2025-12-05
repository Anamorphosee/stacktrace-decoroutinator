import dev.reformator.bytecodeprocessor.plugins.*
import dev.reformator.bytecodeprocessor.plugins.GetOwnerClassProcessor
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

    testImplementation(kotlin("test"))
}

bytecodeProcessor {
    processors = listOf(
        RemoveKotlinStdlibProcessor,
        GetOwnerClassProcessor,
        LoadConstantProcessor
    )
}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:-module"))
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

val kotlinSources = sourceSets.main.get().kotlin
kotlinSources.srcDirs("../../../../provider/src/main/kotlin")
