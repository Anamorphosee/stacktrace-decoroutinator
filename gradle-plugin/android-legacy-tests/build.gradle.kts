import com.android.build.gradle.internal.tasks.factory.dependsOn

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
    useTransformedClassesForCompilation = true
    embedDebugProbesForAndroid = true
}

android {
    namespace = "dev.reformator.stacktracedecoroutinator.gradlepluginandroidlegacytests"
    compileSdk = 35
    defaultConfig {
        minSdk = 14
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
    }
    packaging {
        resources.pickFirsts.add("META-INF/*")
        resources.excludes.add("win32-x86-64/attach_hotspot_windows.dll")
        resources.excludes.add("win32-x86/attach_hotspot_windows.dll")
        resources.excludes.add("META-INF/licenses/*")
    }
    kotlin {
        jvmToolchain(8)
    }
}

dependencies {
    implementation(libs.multidex)
    implementation(project(":stacktrace-decoroutinator-common"))
    runtimeOnly(project(":stacktrace-decoroutinator-generator-android"))
    runtimeOnly(project(":stacktrace-decoroutinator-mh-invoker-android"))
    runtimeOnly(project(":stacktrace-decoroutinator-mh-invoker-jvm"))
    runtimeOnly(project(":stacktrace-decoroutinator-generator"))

    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.jdk8.build)
    androidTestImplementation(libs.junit5.api)
    androidTestImplementation(libs.kotlinx.coroutines.debug.build)
    androidTestRuntimeOnly(libs.androidx.test.runner)
}

afterEvaluate {
    tasks.register("legacyAndroidTest").dependsOn(tasks.named("connectedAndroidTest"))
}
