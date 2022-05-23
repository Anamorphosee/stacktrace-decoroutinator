import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${properties["kotlinxCoroutinesVersion"]}")
    testImplementation("io.github.microutils:kotlin-logging-jvm:${properties["kotlinLoggingJvmVersion"]}")
    testRuntimeOnly("io.ktor:ktor-io-jvm:${properties["ktorVersion"]}")
    testRuntimeOnly("ch.qos.logback:logback-classic:${properties["logbackClassicVersion"]}")
}

tasks.test {
    dependsOn(":stacktrace-decoroutinator-jvm-agent:shadowJar")
    useJUnitPlatform()
    val agentJar = project(":stacktrace-decoroutinator-jvm-agent")
        .tasks
        .named<ShadowJar>("shadowJar")
        .get()
        .outputs
        .files
        .singleFile
    jvmArgs(
        "-javaagent:${agentJar.absolutePath}",
        "-Ddev.reformator.stacktracedecoroutinator.jvmAgentDebugMetadataInfoResolveStrategy=SYSTEM_RESOURCE"
    )
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
