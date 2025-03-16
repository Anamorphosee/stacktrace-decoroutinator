import org.jetbrains.kotlin.gradle.dsl.JvmTarget

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm.version)
    alias(libs.plugins.gradle.plugin.publish)
}

group = "dev.reformator.forcevariantjavaversion"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.logging.jvm)
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
        create("forcevariantjavaversionPlugin") {
            id = "dev.reformator.forcevariantjavaversion"
            implementationClass = "dev.reformator.forcevariantjavaversion.ForceVariantJavaVersionPlugin"
        }
    }
}
