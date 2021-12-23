plugins {
    id("com.android.library")
    id("kotlin-android")
    //kotlin("jvm")
    `maven-publish`
    signing
}

android {
    compileSdk = 31

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(project(":stacktrace-decoroutinator-common"))
    implementation("com.jakewharton.android.repackaged:dalvik-dx:${properties["dalvikDxVersion"]}")

    testImplementation("junit:junit:4.+")

    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${properties["kotlinxCoroutinesVersion"]}")
}

val generateBaseContinuationDexTask = task("generateBaseContinuationDex") {
    val folder = "$projectDir/src/main/resources"
    doLast {
        exec {
            file(folder).mkdir()
            setCommandLine(
                "${android.sdkDirectory}/build-tools/${android.buildToolsVersion}/d8",
                "--min-api", "26",
                "--output", folder,
                "${project(":stacktrace-decoroutinator-common").buildDir}/classes/kotlin/main/kotlin/coroutines/jvm/internal/BaseContinuationImpl.class"
            )
        }
        copy {
            from(folder)
            into(folder)
            include("classes.dex")
            rename("classes.dex", "decoroutinatorBaseContinuation.dex")
        }
        delete("$folder/classes.dex")
    }
    dependsOn(":stacktrace-decoroutinator-common:compileKotlin")
}

tasks.named("preBuild") {
    dependsOn(generateBaseContinuationDexTask)
}
