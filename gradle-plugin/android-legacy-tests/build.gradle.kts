plugins {
    alias(libs.plugins.android.library)
    kotlin("android")
    id("dev.reformator.stacktracedecoroutinator")
}

repositories {
    mavenCentral()
    google()
}

stacktraceDecoroutinator {
    regularDependencyConfigurations.include = emptySet()
    androidDependencyConfigurations.include = emptySet()
    jvmDependencyConfigurations.include = emptySet()
    addJvmRuntimeDependency = false
    addAndroidRuntimeDependency = false
}

android {
    namespace = "dev.reformator.stacktracedecoroutinator.gradlepluginandroidlegacytests"
    compileSdk = 35
    defaultConfig {
        minSdk = 14
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }
    packaging {
        resources.pickFirsts.add("META-INF/*")
    }
    kotlin {
        jvmToolchain(8)
    }
}

dependencies {
    implementation(project(":stacktrace-decoroutinator-common"))
    runtimeOnly(project(":stacktrace-decoroutinator-generator-android"))
    runtimeOnly(project(":stacktrace-decoroutinator-mh-invoker-android"))
    runtimeOnly(project(":stacktrace-decoroutinator-mh-invoker-jvm"))
    runtimeOnly(project(":stacktrace-decoroutinator-generator"))

    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.jdk8.build)
    androidTestImplementation(libs.junit5.api)
    androidTestRuntimeOnly(libs.androidx.test.runner)
}

afterEvaluate {
    tasks.create("legacyAndroidTest").dependsOn(tasks.named("connectedAndroidTest"))
}
