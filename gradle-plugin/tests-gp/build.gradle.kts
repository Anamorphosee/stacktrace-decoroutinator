import dev.reformator.bytecodeprocessor.plugins.GetCurrentFileNameProcessor
import dev.reformator.bytecodeprocessor.plugins.GetCurrentLineNumberProcessor
import dev.reformator.bytecodeprocessor.plugins.RemoveModuleRequiresProcessor
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
    implementation(project(":gradle-plugin:empty-module-tests"))

    //noinspection UseTomlInstead
    testCompileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")

    testRuntimeOnly(project(":stacktrace-decoroutinator-common"))
    testRuntimeOnly(project(":stacktrace-decoroutinator-mh-invoker"))

    testImplementation(project(":test-utils"))
    testImplementation(project(":test-utils-jvm"))
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.jdk8.build)
    testImplementation(project(":gradle-plugin:duplicate-entity-jar-builder", configuration = "duplicateJar"))
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

java {
    sourceCompatibility = JavaVersion.VERSION_1_9
    targetCompatibility = JavaVersion.VERSION_1_9
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

sourceSets {
    test {
        kotlin.destinationDirectory = java.destinationDirectory
    }
}
