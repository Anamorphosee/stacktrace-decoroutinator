buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath(libs.kotlin.gradle.plugin.latest)
    }
}

subprojects {
    group = "dev.reformator.bytecodeprocessor"
    version = "0.0.1-SNAPSHOT"
}
