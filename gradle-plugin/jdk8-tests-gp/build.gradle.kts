import dev.reformator.bytecodeprocessor.plugins.GetCurrentFileNameProcessor
import dev.reformator.bytecodeprocessor.plugins.GetCurrentLineNumberProcessor

plugins {
    kotlin("jvm")
    id("dev.reformator.stacktracedecoroutinator")
    id("dev.reformator.bytecodeprocessor")
}

stacktraceDecoroutinator {
    regularDependencyConfigurations.include = emptySet()
    androidDependencyConfigurations.include = emptySet()
    jvmDependencyConfigurations.include = emptySet()
    addJvmRuntimeDependency = false
    useTransformedClassesForCompilation = true
    embeddedDebugProbesConfigurations.include = setOf("runtimeClasspath", "testRuntimeClasspath")
}

repositories {
    mavenCentral()
}

dependencies {
    //noinspection UseTomlInstead
    testCompileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")

    testRuntimeOnly(project(":stacktrace-decoroutinator-common"))
    testRuntimeOnly(project(":stacktrace-decoroutinator-mh-invoker"))

    testImplementation(project(":test-utils"))
    testImplementation(project(":test-utils-jvm"))
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.jdk8.build)
    testImplementation(libs.kotlinx.coroutines.debug.build)
    testRuntimeOnly(project(":stacktrace-decoroutinator-runtime-settings"))
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
