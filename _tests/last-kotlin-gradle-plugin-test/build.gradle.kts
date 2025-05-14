import dev.reformator.bytecodeprocessor.plugins.*

plugins {
    alias(libs.plugins.kotlin.jvm.latest)
    id("dev.reformator.stacktracedecoroutinator")
    id("dev.reformator.bytecodeprocessor")
}

stacktraceDecoroutinator {
    regularDependencyConfigurations.include = emptySet()
    addJvmRuntimeDependency = false
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

    testCompileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")

    testImplementation(kotlin("test"))
    testImplementation(libs.junit5.api)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.jdk8.latest)
    testImplementation(libs.kotlin.logging.jvm)

    testRuntimeOnly(libs.ktor.io.jvm)
    testRuntimeOnly(libs.logback.classic)
}

bytecodeProcessor {
    val customLoaderJarFile = file("../../test-utils/build/fillConstantProcessor/customLoaderJarUri.txt")
    val customLoaderJarUri = if (customLoaderJarFile.exists()) customLoaderJarFile.readText() else ""
    processors = setOf(
        GetCurrentFileNameProcessor,
        GetCurrentLineNumberProcessor,
        GetOwnerClassProcessor(),
        LoadConstantProcessor(mapOf(
            LoadConstantProcessor.Key(
                "dev.reformator.stacktracedecoroutinator.test.Runtime_testKt",
                "getCustomLoaderJarUri"
            ) to LoadConstantProcessor.Value(customLoaderJarUri)
        ))
    )
}

tasks.test {
    useJUnitPlatform()
}

val kotlinTestSources = sourceSets.test.get().kotlin
kotlinTestSources.srcDirs("../../test-utils/src/main/kotlin")
kotlinTestSources.srcDirs("../../test-utils-jvm/src/main/kotlin")