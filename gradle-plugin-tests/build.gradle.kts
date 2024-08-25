plugins {
    kotlin("jvm")
    //id("dev.reformator.stacktracedecoroutinator")
}

//stacktraceDecoroutinator {
//    _addRuntimeDependency = false
//    require(!_isAndroid)
//}

repositories {
    mavenCentral()
}

dependencies {
    testRuntimeOnly(project(":stacktrace-decoroutinator-common"))

    testImplementation(project(":test-utils"))
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${decoroutinatorVersions["kotlinxCoroutines"]}")
}

tasks.test {
    useJUnitPlatform()
}
