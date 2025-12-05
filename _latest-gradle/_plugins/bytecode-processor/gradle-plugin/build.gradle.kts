import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    alias(libs.plugins.gradle.plugin.publish)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.gradle.plugin.api)
    api(project(":bytecode-processor-api"))
    api(project(":bytecode-processor-plugins"))
    implementation(libs.asm.utils)
    implementation(libs.jackson.core)
    implementation(libs.jackson.kotlin)
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

gradlePlugin {
    plugins {
        create("bytecodeProcessor") {
            id = "dev.reformator.bytecodeprocessor"
            implementationClass = "dev.reformator.bytecodeprocessor.gradleplugin.BytecodeProcessorPlugin"
        }
    }
}

val kotlinSources = sourceSets.main.get().kotlin
kotlinSources.srcDirs("../../../../_plugins/bytecode-processor/gradle-plugin/src/main/kotlin")
