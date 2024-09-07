import dev.reformator.bytecodeprocessor.plugins.GetCurrentFileNameProcessor
import dev.reformator.bytecodeprocessor.plugins.GetCurrentLineNumberProcessor

plugins {
    kotlin("jvm")
    id("dev.reformator.stacktracedecoroutinator")
    id("dev.reformator.bytecodeprocessor")
}

stacktraceDecoroutinator {
    _addCommonDependency = false
    require(!_isAndroid)
}

repositories {
    mavenCentral()
}

dependencies {
    testCompileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")

    testRuntimeOnly(project(":stacktrace-decoroutinator-common"))

    testImplementation(project(":test-utils"))
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${versions["kotlinxCoroutines"]}")
}

bytecodeProcessor {
    processors = setOf(
        GetCurrentFileNameProcessor,
        GetCurrentLineNumberProcessor
    )
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}
