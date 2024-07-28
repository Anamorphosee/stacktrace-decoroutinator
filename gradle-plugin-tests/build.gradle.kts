plugins {
    kotlin("jvm")
    id("dev.reformator.stacktracedecoroutinator")
}

stacktraceDecoroutinator {
    addRuntimeDependency = false
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(project(":test-utils"))
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${decoroutinatorVersions["kotlinxCoroutines"]}")

    testRuntimeOnly(project(":stacktrace-decoroutinator-runtime"))
    //testRuntimeOnly(project(":stacktrace-decoroutinator-generator"))
}

tasks.test {
    useJUnitPlatform()
}
