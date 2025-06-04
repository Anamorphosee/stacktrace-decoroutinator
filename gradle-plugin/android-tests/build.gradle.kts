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
    regularDependencyConfigurations.include = emptySet()
    androidDependencyConfigurations.include = emptySet()
    jvmDependencyConfigurations.include = emptySet()
    addJvmRuntimeDependency = false
    addAndroidRuntimeDependency = false
    useTransformedClassesForCompilation = true
    embedDebugProbesForAndroid = true
    runtimeSettingsDependencyConfigurations.include = emptySet()
}

dependencies {
    androidTestImplementation(project(":test-utils"))
    androidTestImplementation(libs.kotlinx.coroutines.jdk8.build)
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.debug.build)
    androidTestImplementation(libs.junit5.api)

    //noinspection UseTomlInstead
    androidTestCompileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")

    implementation(project(":gradle-plugin:empty-module-tests"))
    androidTestRuntimeOnly(libs.androidx.test.runner)
    androidTestRuntimeOnly(project(":stacktrace-decoroutinator-common"))
    androidTestRuntimeOnly(project(":stacktrace-decoroutinator-mh-invoker"))
    androidTestRuntimeOnly(project(":stacktrace-decoroutinator-runtime-settings"))
}

bytecodeProcessor {
    processors = setOf(
        GetCurrentFileNameProcessor,
        GetCurrentLineNumberProcessor
    )
}

android {
    namespace = "dev.reformator.stacktracedecoroutinator.gradlepluginandroidtests"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
