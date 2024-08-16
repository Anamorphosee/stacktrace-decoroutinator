plugins {
    id("com.android.library")
    kotlin("android")
    //id("dev.reformator.stacktracedecoroutinator")
}

repositories {
    mavenCentral()
    google()
}

//stacktraceDecoroutinator {
//    _addRuntimeDependency = false
//    require(_isAndroid)
//}

dependencies {
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${decoroutinatorVersions["kotlinxCoroutines"]}")
    androidTestImplementation(project(":test-utils"))
    androidTestImplementation("junit:junit:${decoroutinatorVersions["junit4"]}")

    androidTestRuntimeOnly("androidx.test:runner:${decoroutinatorVersions["androidTestRunner"]}")
    androidTestRuntimeOnly(project(":stacktrace-decoroutinator-runtime"))
}

android {
    namespace = "dev.reformator.stacktracedecoroutinator.gradlepluginandroidtests"
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
