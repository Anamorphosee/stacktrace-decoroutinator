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
    _addCommonDependency = false
    require(_isAndroid)
}

dependencies {
    androidTestImplementation(project(":test-utils"))
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${decoroutinatorVersions["kotlinxCoroutines"]}")
    androidTestImplementation("junit:junit:${decoroutinatorVersions["junit4"]}")

    androidTestCompileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")

    androidTestRuntimeOnly("androidx.test:runner:${decoroutinatorVersions["androidTestRunner"]}")
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
