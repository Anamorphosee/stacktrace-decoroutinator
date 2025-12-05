buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    dependencies {
        classpath(libs.kotlin.gradle.plugin.latest)
        classpath(libs.android.gradle.plugin)
    }
}

subprojects {
    group = "dev.reformator.stacktracedecoroutinator-latest-gradle"
    version = "1.0.0-SNAPSHOT"
}
