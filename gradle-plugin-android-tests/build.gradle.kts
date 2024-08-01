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
    implementation(project(":test-utils"))

    runtimeOnly(project(":stacktrace-decoroutinator-runtime"))

    androidTestImplementation("androidx.test:runner:${decoroutinatorVersions["androidTestRunner"]}")
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
