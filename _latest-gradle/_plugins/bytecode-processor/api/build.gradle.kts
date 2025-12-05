import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    api(libs.asm.utils)
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
kotlinSources.srcDirs("../../../../_plugins/bytecode-processor/api/src/main/kotlin")
