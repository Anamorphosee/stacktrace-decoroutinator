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
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${versions["kotlinxCoroutines"]}")
    testImplementation(kotlin("test"))
    testImplementation(project(":test-utils"))
}

tasks.test {
    useJUnitPlatform()
    extensions.configure(JacocoTaskExtension::class) {
        includes = listOf("JacocoInstrumentedMethodTest*")
    }
    systemProperty("testReloadBaseConfiguration", false)
}

val testReloadBaseConfigurationTask = tasks.create<Test>("testReloadBaseConfiguration") {
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
