import dev.reformator.bytecodeprocessor.plugins.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("dev.reformator.bytecodeprocessor")
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")

    implementation(project(":stacktrace-decoroutinator-common"))
    implementation("org.junit.jupiter:junit-jupiter-api:${versions["junit5"]}")
    implementation("junit:junit:${versions["junit4"]}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${versions["kotlinxCoroutines"]}")
    implementation("io.github.oshai:kotlin-logging-jvm:${versions["kotlinLoggingJvm"]}")

    runtimeOnly("io.ktor:ktor-io-jvm:${versions["ktor"]}")
    runtimeOnly("ch.qos.logback:logback-classic:${versions["logbackClassic"]}")
}

bytecodeProcessor {
    processors = setOf(
        RemoveModuleRequiresProcessor("dev.reformator.bytecodeprocessor.intrinsics", "intrinsics"),
        GetCurrentFileNameProcessor,
        GetCurrentLineNumberProcessor,
        GetOwnerClassProcessor()
    )
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_9
    targetCompatibility = JavaVersion.VERSION_1_9
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:-module"))
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

sourceSets {
    main {
        kotlin.destinationDirectory = java.destinationDirectory
    }
}
