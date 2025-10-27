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
        //noinspection UseTomlInstead
        classpath("dev.reformator.bytecodeprocessor:bytecode-processor-plugins")
        //noinspection UseTomlInstead
        classpath("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")
        //noinspection UseTomlInstead
        classpath("dev.reformator.gradle-plugin-test:gradle-plugin-test")
    }
}

plugins {
    alias(libs.plugins.nmcp)
}

subprojects {
    group = "dev.reformator.stacktracedecoroutinator"
    version = "2.5.8-SNAPSHOT"
}

nmcp {
    publishAggregation {
        project(":stacktrace-decoroutinator-common")
        project(":stacktrace-decoroutinator-generator-jvm")
        project(":stacktrace-decoroutinator-generator-android")
        project(":stacktrace-decoroutinator-gradle-plugin")
        project(":stacktrace-decoroutinator-jvm")
        project(":stacktrace-decoroutinator-jvm-agent")
        project(":stacktrace-decoroutinator-jvm-agent-common")
        project(":stacktrace-decoroutinator-provider")
        project(":stacktrace-decoroutinator-mh-invoker")
        project(":stacktrace-decoroutinator-mh-invoker-android")
        project(":stacktrace-decoroutinator-mh-invoker-jvm")
        project(":stacktrace-decoroutinator-runtime-settings")
        username = properties["sonatype.username"] as String?
        password = properties["sonatype.password"] as String?
        publicationType = "USER_MANAGED"
    }
}
