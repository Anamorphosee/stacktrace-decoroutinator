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
    alias(libs.plugins.nmcp.aggregation)
}

subprojects {
    group = "dev.reformator.stacktracedecoroutinator"
    version = "2.5.9-SNAPSHOT"
}

repositories {
    mavenCentral()
}

nmcpAggregation {
    centralPortal {
        username = properties["sonatype.username"] as String?
        password = properties["sonatype.password"] as String?
        publishingType = "USER_MANAGED"
    }
    publishAllProjectsProbablyBreakingProjectIsolation()
}
