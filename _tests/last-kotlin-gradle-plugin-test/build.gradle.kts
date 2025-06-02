import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.reformator.bytecodeprocessor.plugins.*
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import kotlin.jvm.java

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
    runtimeOnly(files("../../provider/build/libs/").asFileTree)
    implementation(files("../../common/build/libs/").asFileTree)
    implementation(files("../../mh-invoker/build/libs/").asFileTree)
    implementation(files("../../generator/build/libs/").asFileTree)
    runtimeOnly(libs.asm.utils)

    //noinspection UseTomlInstead
    testCompileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")

    testImplementation(kotlin("test"))
    testImplementation(libs.junit5.api)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.jdk8.latest)
    testImplementation(libs.kotlin.logging.jvm)
    testImplementation(libs.kotlinx.coroutines.debug.latest)

    testRuntimeOnly(libs.ktor.io.jvm)
    testRuntimeOnly(libs.logback.classic)
}

bytecodeProcessor {
    processors = setOf(
        GetCurrentFileNameProcessor,
        GetCurrentLineNumberProcessor,
        GetOwnerClassProcessor()
    )
}

val fillConstantProcessorTask = tasks.register("fillConstantProcessor") {
    val customLoaderProject = project(":custom-loader")
    val customLoaderJarTask = customLoaderProject.tasks.named<ShadowJar>("shadowJar")
    dependsOn(customLoaderJarTask)
    doLast {
        val customLoaderJarUri = customLoaderJarTask.get().archiveFile.get().asFile.toURI().toString()
        bytecodeProcessor {
            processors += LoadConstantProcessor(mapOf(
                LoadConstantProcessor.Key(
                    "dev.reformator.stacktracedecoroutinator.test.Runtime_testKt",
                    "getCustomLoaderJarUri"
                ) to LoadConstantProcessor.Value(customLoaderJarUri)
            ))
        }
    }
}

tasks.withType(KotlinJvmCompile::class.java) {
    dependsOn(fillConstantProcessorTask)
}

tasks.test {
    useJUnitPlatform()
}

val kotlinTestSources = sourceSets.test.get().kotlin
kotlinTestSources.srcDirs("../../test-utils/src/main/kotlin")
kotlinTestSources.srcDirs("../../test-utils-jvm/src/main/kotlin")