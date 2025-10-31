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
    namespace = "dev.reformator.stacktracedecoroutinator.generatorjvm.androidwronggeneratortests"
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
    androidTestImplementation(project(":stacktrace-decoroutinator-common"))
    androidTestImplementation(libs.kotlinx.coroutines.jdk8.build)
    androidTestImplementation(libs.junit4)

    androidTestRuntimeOnly(libs.androidx.test.runner)
    androidTestRuntimeOnly(project(":test-utils:base-continuation-accessor-reflect-stub"))
    androidTestRuntimeOnly(project(":stacktrace-decoroutinator-mh-invoker-android"))
    androidTestRuntimeOnly(project(":stacktrace-decoroutinator-generator-jvm"))
}

afterEvaluate {
    configurations["debugAndroidTestRuntimeClasspath"].attributes.attribute(decoroutinatorTransformedBaseContinuationAttribute, true)
}
