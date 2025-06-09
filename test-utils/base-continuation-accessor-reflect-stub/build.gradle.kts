import dev.reformator.bytecodeprocessor.plugins.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("dev.reformator.bytecodeprocessor")
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(project(":intrinsics"))
    implementation(project(":stacktrace-decoroutinator-provider"))
}

bytecodeProcessor {
    processors = setOf(
        ChangeClassNameProcessor(mapOf(
            "dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation" to "kotlin.coroutines.jvm.internal.BaseContinuationImpl",
        ))
    )
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:-module"))
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}
