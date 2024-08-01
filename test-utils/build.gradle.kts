plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.junit.jupiter:junit-jupiter-api:${decoroutinatorVersions["junit5"]}")
    implementation("junit:junit:${decoroutinatorVersions["junit4"]}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${decoroutinatorVersions["kotlinxCoroutines"]}")
    implementation("io.github.microutils:kotlin-logging-jvm:${decoroutinatorVersions["kotlinLoggingJvm"]}")

    runtimeOnly("io.ktor:ktor-io-jvm:${decoroutinatorVersions["ktor"]}")
    runtimeOnly("ch.qos.logback:logback-classic:${decoroutinatorVersions["logbackClassic"]}")
}

kotlin {
    jvmToolchain(8)
}
