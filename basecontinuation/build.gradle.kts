import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("dev.reformator.stacktracedecoroutinator.apply-intrinsics")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":stacktrace-decoroutinator-provider"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
        freeCompilerArgs.add("-Xallow-kotlin-package")
    }
}
