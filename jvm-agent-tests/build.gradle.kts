import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

java {
    sourceCompatibility = JavaVersion.VERSION_1_9
    targetCompatibility = JavaVersion.VERSION_1_9
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

sourceSets {
    main {
        kotlin.destinationDirectory = java.destinationDirectory
    }
}
