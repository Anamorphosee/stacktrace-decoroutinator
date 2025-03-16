import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.reformator.bytecodeprocessor.plugins.RemoveKotlinStdlibProcessor
import org.jetbrains.dokka.gradle.AbstractDokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    alias(libs.plugins.dokka)
    alias(libs.plugins.shadow)
    `maven-publish`
    signing
    id("dev.reformator.bytecodeprocessor")
    id("dev.reformator.forcevariantjavaversion")
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(kotlin("stdlib"))

    implementation(project(":stacktrace-decoroutinator-jvm-agent-common")) {
        exclude(group = "org.jetbrains.kotlin")
    }

    testImplementation(project(":test-utils"))
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.jdk8)
}

bytecodeProcessor {
    processors = setOf(RemoveKotlinStdlibProcessor())
}

val shadowJarTask = tasks.named<ShadowJar>("shadowJar") {
    manifest {
        attributes(mapOf(
            "Premain-Class" to "dev.reformator.stacktracedecoroutinator.jvmagent.DecoroutinatorAgentKt"
        ))
    }
    archiveClassifier.set("")
    relocate("org.objectweb.asm", "dev.reformator.stacktracedecoroutinator.asmrepack")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.test {
    useJUnitPlatform()
    dependsOn(project(":jvm-agent:tests-ja").tasks.test)
    dependsOn(project(":jvm-agent:jdk8-tests-ja").tasks.test)
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

val dokkaJavadocsJar = task("dokkaJavadocsJar", Jar::class) {
    val dokkaJavadocTask = tasks.named<AbstractDokkaTask>("dokkaJavadoc").get()
    dependsOn(dokkaJavadocTask)
    archiveClassifier.set("javadoc")
    from(dokkaJavadocTask.outputDirectory)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            shadow.component(this)
            artifact(dokkaJavadocsJar)
            artifact(tasks.named("kotlinSourcesJar"))
            pom {
                name.set("Stacktrace-decoroutinator JVM agent.")
                description.set("JVM agent for recovering stack trace in exceptions thrown in Kotlin coroutines.")
                url.set("https://stacktracedecoroutinator.reformator.dev")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        name.set("Denis Berestinskii")
                        email.set("berestinsky@gmail.com")
                        url.set("https://github.com/Anamorphosee")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/Anamorphosee/stacktrace-decoroutinator.git")
                    developerConnection.set("scm:git:ssh://github.com:Anamorphosee/stacktrace-decoroutinator.git")
                    url.set("http://github.com/Anamorphosee/stacktrace-decoroutinator/tree/master")
                }
            }
        }
    }
    repositories {
        maven {
            name = "sonatype"
            val releaseRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) {
                snapshotRepoUrl
            } else {
                releaseRepoUrl
            }
            credentials {
                username = properties["sonatype.username"] as String?
                password = properties["sonatype.password"] as String?
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["maven"])
}
