plugins {
    alias(libs.plugins.kotlin.jvm.latest)
    id("dev.reformator.stacktracedecoroutinator")
}

stacktraceDecoroutinator {
    regularDependencyConfigurations.include = emptySet()
    addJvmRuntimeDependency = false
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(files("../../provider/build/libs/").asFileTree)
    implementation(files("../../common/build/libs/").asFileTree)
    implementation(files("../../mh-invoker/build/libs/").asFileTree)

//    testCompileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")

    testImplementation(files("../../test-utils/build/libs/").asFileTree)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit5.api)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.jdk8.latest)
    testImplementation(libs.kotlin.logging.jvm)

    testRuntimeOnly(libs.ktor.io.jvm)
    testRuntimeOnly(libs.logback.classic)
}

tasks.test {
    useJUnitPlatform()
}

val kotlinTestSources = sourceSets.test.get().kotlin
kotlinTestSources.srcDirs("../../test-utils/src/main/kotlin")