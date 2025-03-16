buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    dependencies {
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.android.gradle.plugin)
        classpath(libs.shadow.gradle.plugin)
        classpath("dev.reformator.bytecodeprocessor:bytecode-processor-plugins")
        classpath("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")
        classpath("dev.reformator.decoroutinatortest:decoroutinatortest")
    }
}

subprojects {
    group = "dev.reformator.stacktracedecoroutinator"
    version = "2.4.9-SNAPSHOT"
}
