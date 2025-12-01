import com.android.build.gradle.internal.tasks.R8Task
import dev.reformator.bytecodeprocessor.plugins.GetCurrentFileNameProcessor
import dev.reformator.bytecodeprocessor.plugins.LoadConstantProcessor

plugins {
    alias(libs.plugins.android.application)
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
    implementation(project(":test-utils"))
    implementation(libs.kotlinx.coroutines.jdk8.build)
    implementation(libs.junit4)
    implementation(libs.kotlinx.coroutines.debug.build)
    implementation(libs.junit5.api)
    implementation(project(":gradle-plugin:empty-module-tests"))
    implementation(libs.androidx.test.rules)

    runtimeOnly(libs.androidx.test.runner)
    runtimeOnly(project(":stacktrace-decoroutinator-common"))
    runtimeOnly(project(":stacktrace-decoroutinator-mh-invoker"))
    runtimeOnly(project(":stacktrace-decoroutinator-runtime-settings"))

    //noinspection UseTomlInstead
    compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")
}

val minifyDebugMappingFileDevicePath = "/sdcard/debugMapping.txt"

bytecodeProcessor {
    processors = listOf(
        GetCurrentFileNameProcessor,
        LoadConstantProcessor
    )
    initContext {
        LoadConstantProcessor.addValues(
            context = this,
            valuesByKeys = mapOf("minifyDebugMappingFile" to minifyDebugMappingFileDevicePath)
        )
    }
}

afterEvaluate {
    val minifyDebugWithR8Task = tasks.named<R8Task>("minifyDebugWithR8")

    val pushMinifyDebugMappingFileTask = tasks.register<Exec>("pushMinifyDebugMappingFile") {
        commandLine(
            "${android.sdkDirectory}/platform-tools/adb",
            "push",
            minifyDebugWithR8Task.get().mappingFile.get().asFile.absolutePath,
            minifyDebugMappingFileDevicePath
        )
        dependsOn(minifyDebugWithR8Task)
    }

    tasks.matching { it.name.startsWith("connected") && it.name.endsWith("AndroidTest") }.configureEach {
        dependsOn(pushMinifyDebugMappingFileTask)
    }
}

android {
    namespace = "dev.reformator.stacktracedecoroutinator.gradlepluginandroidtests"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.reformator.stacktracedecoroutinator.gradlepluginandroidtests"
        versionCode = 1
        versionName = "1.0"
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
    buildTypes {
        debug {
            isMinifyEnabled = true
            isDebuggable = false
            testProguardFiles(decoroutinatorAndroidProGuardRules(), "proguard-rules.pro")
            proguardFiles(decoroutinatorAndroidProGuardRules(), "proguard-rules.pro")
        }
    }
}
