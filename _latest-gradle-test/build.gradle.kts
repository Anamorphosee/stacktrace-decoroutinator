buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    dependencies {
        classpath(libs.kotlin.gradle.plugin.latest)
        classpath(libs.android.gradle.plugin)
        classpath(fileTree("../gradle-plugin/build/libs") { include("*.jar") })
        classpath(libs.kotlin.logging.jvm)
        classpath(fileTree("../provider/build/libs") { include("*.jar") })
        classpath(fileTree("../class-transformer/build/libs") { include("*.jar") })
        classpath(fileTree("../spec-method-builder/build/libs") { include("*.jar") })
        classpath(libs.kotlin.metadata.jvm)
        classpath(libs.asm.utils)
    }
}

subprojects {
    group = "dev.reformator.stacktracedecoroutinator-latest-gradle"
    version = "1.0.0-SNAPSHOT"
}
