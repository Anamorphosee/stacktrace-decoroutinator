import dev.reformator.bytecodeprocessor.plugins.*

plugins {
    alias(libs.plugins.kotlin.jvm.latest)
    id("dev.reformator.bytecodeprocessor")
    alias(libs.plugins.shadow)
}

repositories {
    mavenCentral()
}

dependencies {
    //noinspection UseTomlInstead
    compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")
    implementation(libs.kotlinx.coroutines.jdk8.build)
    implementation(libs.junit5.api)
}

bytecodeProcessor {
    processors = setOf(
        GetCurrentFileNameProcessor,
        GetOwnerClassProcessor
    )
}

tasks.test {
    useJUnitPlatform()
}

val kotlinSources = sourceSets.main.get().kotlin
kotlinSources.srcDirs("../../../test-utils/custom-loader/src/main/kotlin")
