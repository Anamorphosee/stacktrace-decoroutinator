import com.android.build.gradle.internal.tasks.factory.dependsOn

plugins {
    alias(libs.plugins.android.library)
    kotlin("android")
    id("decoroutinatorTransformBaseContinuation")
}

repositories {
    mavenCentral()
    google()
}

android {
    namespace = "dev.reformator.stacktracedecoroutinator.mhinvokerandroid.legacytests"
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

    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.jdk8.build)
    androidTestImplementation(libs.junit5.api)
    androidTestRuntimeOnly(libs.androidx.test.runner)
    androidTestRuntimeOnly(project(":test-utils:base-continuation-accessor-reflect-stub"))
}

afterEvaluate {
    configurations["debugRuntimeClasspath"].attributes.attribute(decoroutinatorTransformedBaseContinuationAttribute, true)
    configurations["releaseRuntimeClasspath"].attributes.attribute(decoroutinatorTransformedBaseContinuationAttribute, true)
    configurations["debugAndroidTestRuntimeClasspath"].attributes.attribute(decoroutinatorTransformedBaseContinuationAttribute, true)
}

afterEvaluate {
    tasks.register("legacyAndroidTest").dependsOn(tasks.named("connectedAndroidTest"))
}
