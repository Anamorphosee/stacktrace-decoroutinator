import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm.latest)
    id("dev.reformator.stacktracedecoroutinator")
}

stacktraceDecoroutinator {
    dependencyConfigurations.include = emptySet()
    addJvmRuntimeDependency = false
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(files("../../provider/build/libs/").asFileTree)
    implementation(files("../../common/build/libs/").asFileTree)

//    testCompileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")

    testImplementation(files("../../test-utils/build/libs/").asFileTree)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit5.api)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.jdk8)
    testImplementation(libs.kotlin.logging.jvm)

    testRuntimeOnly(libs.ktor.io.jvm)
    testRuntimeOnly(libs.logback.classic)
}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_9
    targetCompatibility = JavaVersion.VERSION_1_9
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}
