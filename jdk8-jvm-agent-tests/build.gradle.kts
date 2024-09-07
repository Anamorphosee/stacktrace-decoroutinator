import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    jacoco
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(project(":test-utils"))
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${versions["kotlinxCoroutines"]}")
}

tasks.test {
    useJUnitPlatform()
    val shadowJarTask = project(":stacktrace-decoroutinator-jvm-agent").tasks.named<ShadowJar>("shadowJar")
    dependsOn(shadowJarTask)
    val agentJar = shadowJarTask.get().outputs.files.singleFile
    jvmArgs(
        "-javaagent:${agentJar.absolutePath}",
        "-Ddev.reformator.stacktracedecoroutinator.jvmAgentDebugMetadataInfoResolveStrategy=SYSTEM_RESOURCE"
    )
    extensions.configure(JacocoTaskExtension::class) {
        includes = listOf("JacocoInstrumentedMethodTest*")
    }
}

kotlin {
    jvmToolchain(8)
}
