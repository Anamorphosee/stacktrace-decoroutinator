import dev.reformator.bytecodeprocessor.plugins.GetCurrentFileNameProcessor
import dev.reformator.bytecodeprocessor.plugins.GetCurrentLineNumberProcessor

plugins {
    alias(libs.plugins.android.library)
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
    addAndroidRuntimeDependency = false
}

dependencies {
    androidTestImplementation(project(":test-utils"))
    androidTestImplementation(libs.kotlinx.coroutines.jdk8.build)
    androidTestImplementation(libs.junit4)

    androidTestCompileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")

    androidTestRuntimeOnly(libs.androidx.test.runner)
    androidTestRuntimeOnly(project(":stacktrace-decoroutinator-common"))
    androidTestRuntimeOnly(project(":stacktrace-decoroutinator-mh-invoker"))
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
