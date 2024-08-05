import com.android.build.gradle.internal.tasks.factory.dependsOn
import org.jetbrains.dokka.gradle.AbstractDokkaTask

plugins {
    `java-gradle-plugin`
    kotlin("jvm")
    id("org.jetbrains.dokka")
    `maven-publish`
    signing
}

repositories {
    mavenCentral()
}

@Suppress("UnstableApiUsage")
gradlePlugin {
    website = "https://github.com/Anamorphosee/stacktrace-decoroutinator"
    vcsUrl = "https://github.com/Anamorphosee/stacktrace-decoroutinator.git"
    plugins {
        create("decoroutinatorPlugin") {
            id = "dev.reformator.stacktracedecoroutinator"
            implementationClass = "dev.reformator.stacktracedecoroutinator.gradleplugin.DecoroutinatorPlugin"
            displayName = "Stacktrace Decoroutinator Gradle Plugin"
            description = "Gradle plugin for recovering stack trace in exceptions thrown in Kotlin coroutines"
            tags = listOf("kotlin", "coroutines", "debug", "kotlin-coroutines")
        }
    }
}

dependencies {
    implementation(project(":stacktrace-decoroutinator-runtime"))
    implementation(project(":stacktrace-decoroutinator-generator"))
    implementation("io.github.microutils:kotlin-logging-jvm:${decoroutinatorVersions["kotlinLoggingJvm"]}")

    testImplementation(kotlin("test"))
}

tasks.named("processResources").get().doLast {
    val propertiesFile = layout.buildDirectory.get()
        .dir("resources")
        .dir("main")
        .file("dev.reformator.stacktracedecoroutinator.gradleplugin.properties")
    propertiesFile.asFile.outputStream().writer().use { output ->
        output.write("version=$version\n")
    }
}

kotlin {
    jvmToolchain(8)
}

tasks.test {
    useJUnitPlatform()
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
            from(components["java"])
            artifact(dokkaJavadocsJar)
            artifact(tasks.named("kotlinSourcesJar"))
            pom {
                name.set("Stacktrace-decoroutinator Gradle plugin.")
                description.set("Library for recovering stack trace in exceptions thrown in Kotlin coroutines.")
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

tasks.named("generateMetadataFileForMavenPublication").dependsOn(
    tasks.named("dokkaJavadocsJar"),
    tasks.named("kotlinSourcesJar")
)

signing {
    useGpgCmd()
    sign(publishing.publications["maven"])
}
