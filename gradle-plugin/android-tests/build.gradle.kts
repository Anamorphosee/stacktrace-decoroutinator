import dev.reformator.bytecodeprocessor.plugins.GetCurrentFileNameProcessor
import dev.reformator.bytecodeprocessor.plugins.GetCurrentLineNumberProcessor

plugins {
    id("com.android.library")
    kotlin("android")
    id("dev.reformator.stacktracedecoroutinator")
    id("dev.reformator.bytecodeprocessor")
}

repositories {
    mavenCentral()
    google()
}

stacktraceDecoroutinator {
    dependencyConfigurations.include = emptySet()
    addJvmRuntimeDependency = false
}

dependencies {
    androidTestImplementation(project(":test-utils"))
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${versions["kotlinxCoroutines"]}")
    androidTestImplementation("junit:junit:${versions["junit4"]}")

    androidTestCompileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")

    androidTestRuntimeOnly("androidx.test:runner:${versions["androidTestRunner"]}")
    androidTestRuntimeOnly(project(":stacktrace-decoroutinator-common"))
}

bytecodeProcessor {
    processors = setOf(
        GetCurrentFileNameProcessor,
        GetCurrentLineNumberProcessor
    )
}

android {
    namespace = "dev.reformator.stacktracedecoroutinator.gradlepluginandroidtests"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    packaging {
        resources.pickFirsts.add("META-INF/*")
    }
    kotlin {
        jvmToolchain(8)
    }
}
