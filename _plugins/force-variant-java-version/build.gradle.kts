import org.gradle.kotlin.dsl.versions
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    kotlin("jvm") version versions["kotlin"]
    id("com.gradle.plugin-publish") version versions["pluginPublish"]
}

group = "dev.reformator.forcevariantjavaversion"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.oshai:kotlin-logging-jvm:${versions["kotlinLoggingJvm"]}")
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
