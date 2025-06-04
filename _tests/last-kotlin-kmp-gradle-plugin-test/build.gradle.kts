import dev.reformator.bytecodeprocessor.plugins.GetCurrentFileNameProcessor
import dev.reformator.bytecodeprocessor.plugins.GetCurrentLineNumberProcessor
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlin.multiplatform.latest)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform.latest)
    alias(libs.plugins.compose.compiler.latest)
    id("dev.reformator.stacktracedecoroutinator")
    id("dev.reformator.bytecodeprocessor")
}

stacktraceDecoroutinator {
    regularDependencyConfigurations.include = emptySet()
    androidDependencyConfigurations.include = emptySet()
    jvmDependencyConfigurations.include = emptySet()
    addJvmRuntimeDependency = false
    addAndroidRuntimeDependency = false
    useTransformedClassesForCompilation = true
}

bytecodeProcessor {
    processors = setOf(
        GetCurrentFileNameProcessor,
        GetCurrentLineNumberProcessor
    )
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm("desktop")
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "composeApp"
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                outputFileName = "composeApp.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        // Serve sources to debug inside browser
                        add(rootDirPath)
                        add(projectDirPath)
                    }
                }
            }
        }
        binaries.executable()
    }
    
    sourceSets {
        val desktopMain by getting
        
        androidMain.dependencies {
            implementation(compose.preview)
            //noinspection UseTomlInstead
            implementation("androidx.activity:activity-compose:1.10.1")
            implementation(files("../../generator-android/build/outputs/aar/stacktrace-decoroutinator-generator-android-release.aar"))
            implementation(files("../../mh-invoker-android/build/outputs/aar/stacktrace-decoroutinator-mh-invoker-android-release.aar"))
            implementation(libs.kotlin.logging.jvm)
            runtimeOnly(libs.dalvik.dx)
            runtimeOnly(libs.logback.classic)
        }
        androidInstrumentedTest.dependencies {
            //noinspection UseTomlInstead
            compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")
            runtimeOnly(libs.androidx.test.runner)
            implementation(kotlin("test"))
            implementation(files("../../test-utils/build/libs/").asFileTree)
            implementation(libs.junit5.api)
            runtimeOnly(libs.ktor.io.jvm)
        }
        androidUnitTest.dependencies {
            //noinspection UseTomlInstead
            compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")
            implementation(kotlin("test"))
            implementation(files("../../test-utils/build/libs/").asFileTree)
            implementation(files("../../generator/build/libs/").asFileTree)
            implementation(files("../../mh-invoker/build/libs/").asFileTree)
            runtimeOnly(libs.asm.utils)
            implementation(libs.junit5.api)
            runtimeOnly(libs.ktor.io.jvm)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            //noinspection UseTomlInstead
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.4")
            //noinspection UseTomlInstead
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
            implementation(files("../../provider/build/libs/").asFileTree)
            implementation(files("../../common/build/libs/").asFileTree)
            implementation(files("../../runtime-settings/build/libs/").asFileTree)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            //noinspection UseTomlInstead
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
            implementation(files("../../generator/build/libs/").asFileTree)
            implementation(files("../../mh-invoker/build/libs/").asFileTree)
            runtimeOnly(libs.asm.utils)
        }
    }
}

android {
    namespace = "org.example.project"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.example.project"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        resources.merges.add("META-INF/services/*")
        resources.pickFirsts.add("META-INF/*")
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)

}

compose.desktop {
    application {
        mainClass = "org.example.project.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "org.example.project"
            packageVersion = "1.0.0"
        }
    }
}
