import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.dokka.gradle.AbstractDokkaTask

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
    id("com.github.johnrengelman.shadow")
    `maven-publish`
    signing
    jacoco
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":stacktrace-decoroutinator-jvm-agent-common")) {
        exclude(group = "org.jetbrains.kotlin")
    }

    testImplementation(project(":test-utils"))
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${decoroutinatorVersions["kotlinxCoroutines"]}")
}

val shadowJarTask = tasks.named<ShadowJar>("shadowJar") {
    manifest {
        attributes(mapOf(
            "Premain-Class" to "dev.reformator.stacktracedecoroutinator.jvmagent.DecoroutinatorAgent"
        ))
    }
    archiveClassifier.set("")
    relocate("org.objectweb.asm", "dev.reformator.asmrepack")
}

tasks.test {
    useJUnitPlatform()
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
