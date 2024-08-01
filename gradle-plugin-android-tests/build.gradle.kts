plugins {
    id("com.android.application")
    kotlin("android")
    id("dev.reformator.stacktracedecoroutinator")
}

repositories {
    mavenCentral()
    google()
}

stacktraceDecoroutinator {
    _addRuntimeDependency = false
}

dependencies {
    androidTestImplementation(project(":test-utils"))
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${decoroutinatorVersions["kotlinxCoroutines"]}")
    androidTestImplementation("androidx.test:runner:${decoroutinatorVersions["androidTestRunner"]}")

    androidTestRuntimeOnly(project(":stacktrace-decoroutinator-runtime"))
}

android {
    namespace = "dev.reformator.stacktracedecoroutinator"
    compileSdk = 26
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
