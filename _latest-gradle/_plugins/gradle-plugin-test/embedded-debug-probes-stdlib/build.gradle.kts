import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    //noinspection UseTomlInstead
    compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")
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
kotlinSources.srcDirs("../../../../gradle-plugin/embedded-debug-probes-stdlib/src/main/kotlin")
