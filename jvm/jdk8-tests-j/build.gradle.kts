plugins {
    kotlin("jvm")
    jacoco
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(project(":stacktrace-decoroutinator-common"))
    testImplementation(project(":stacktrace-decoroutinator-provider"))
    testImplementation(project(":stacktrace-decoroutinator-jvm"))
    testImplementation(libs.kotlinx.coroutines.jdk8.build)
    testImplementation(kotlin("test"))
    testImplementation(project(":test-utils"))
    testImplementation(project(":test-utils-jvm"))
}

tasks.test {
    useJUnitPlatform()
    extensions.configure(JacocoTaskExtension::class) {
        includes = listOf("JacocoInstrumentedMethodTest*")
    }
    systemProperty("testReloadBaseConfiguration", false)
}

val testReloadBaseConfigurationTask = tasks.register<Test>("testReloadBaseConfiguration") {
    useJUnitPlatform()
    classpath = tasks.test.get().classpath
    extensions.configure(JacocoTaskExtension::class) {
        excludes = listOf("*")
    }
    systemProperty("testReloadBaseConfiguration", true)
}
tasks.test.get().dependsOn(testReloadBaseConfigurationTask)

kotlin {
    jvmToolchain(8)
}
