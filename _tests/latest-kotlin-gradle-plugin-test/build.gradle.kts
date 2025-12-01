buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    dependencies {
        classpath(libs.shadow.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm.latest)
    id("dev.reformator.stacktracedecoroutinator")
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
    runtimeOnly(files("../../provider/build/libs/").asFileTree)
    implementation(files("../../common/build/libs/").asFileTree)
    implementation(files("../../mh-invoker/build/libs/").asFileTree)
    implementation(files("../../generator-jvm/build/libs/").asFileTree)
    implementation(files("../../runtime-settings/build/libs/").asFileTree)
    implementation(files("../../class-transformer/build/libs/").asFileTree)
    implementation(files("../../spec-method-builder/build/libs/").asFileTree)
    runtimeOnly(libs.asm.utils)

    //noinspection UseTomlInstead
    testCompileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")

    implementation(libs.kotlinx.coroutines.jdk8.latest)
    implementation(libs.kotlinx.coroutines.debug.latest)
    testImplementation(kotlin("test"))
    testImplementation(project(":test-utils"))
    testImplementation(files("../../test-utils/retrace-repack/build/libs/").asFileTree)
}

tasks.test {
    useJUnitPlatform()
}
