import dev.reformator.bytecodeprocessor.plugins.GetCurrentFileNameProcessor
import dev.reformator.bytecodeprocessor.plugins.GetCurrentLineNumberProcessor
import dev.reformator.bytecodeprocessor.plugins.RemoveModuleRequiresProcessor

plugins {
    kotlin("jvm")
    id("dev.reformator.stacktracedecoroutinator")
    id("dev.reformator.bytecodeprocessor")
}

stacktraceDecoroutinator {
    dependencyConfigurations.include = emptySet()
    addJvmRuntimeDependency = false
}

repositories {
    mavenCentral()
}

dependencies {
    testCompileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")

    testRuntimeOnly(project(":stacktrace-decoroutinator-common"))

    testImplementation(project(":test-utils"))
    testImplementation(project(":test-utils-jvm"))
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${versions["kotlinxCoroutines"]}")
}

bytecodeProcessor {
    processors = setOf(
        RemoveModuleRequiresProcessor("dev.reformator.bytecodeprocessor.intrinsics", "intrinsics"),
        GetCurrentFileNameProcessor,
        GetCurrentLineNumberProcessor
    )
}

tasks.test {
    useJUnitPlatform()
}
