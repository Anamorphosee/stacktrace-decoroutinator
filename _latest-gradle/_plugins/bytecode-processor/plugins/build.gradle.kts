import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":bytecode-processor-api"))
    implementation(project(":bytecode-processor-intrinsics"))
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
kotlinSources.srcDirs("../../../../_plugins/bytecode-processor/plugins/src/main/kotlin")
