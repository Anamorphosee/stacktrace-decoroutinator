import com.android.build.gradle.internal.tasks.factory.dependsOn
import org.jetbrains.dokka.gradle.AbstractDokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
    `maven-publish`
    signing
    jacoco
    id("dev.reformator.stacktracedecoroutinator.downgrade-classes")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":stacktrace-decoroutinator-runtime"))
    implementation(project(":stacktrace-decoroutinator-jvm-agent-common"))
    implementation("net.bytebuddy:byte-buddy-agent:${decoroutinatorVersions["byteBuddy"]}")

    testImplementation(kotlin("test"))
    testImplementation(project(":test-utils"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${decoroutinatorVersions["kotlinxCoroutines"]}")
}

tasks.test {
    useJUnitPlatform()
    extensions.configure(JacocoTaskExtension::class) {
        includes = listOf("JacocoInstrumentedMethodTest*")
    }
    systemProperty("testReloadBaseConfiguration", false)
}

val testReloadBaseConfigurationTestName = "testReloadBaseConfiguration"
tasks.create<Test>(testReloadBaseConfigurationTestName) {
    useJUnitPlatform()
    classpath = tasks.test.get().classpath
    extensions.configure(JacocoTaskExtension::class) {
        excludes = listOf("*")
    }
    systemProperty("testReloadBaseConfiguration", true)
}
tasks.test.dependsOn(tasks.named<Test>(testReloadBaseConfigurationTestName))

java {
    sourceCompatibility = JavaVersion.VERSION_1_9
    targetCompatibility = JavaVersion.VERSION_1_9
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
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
            from(components["java"])
            artifact(dokkaJavadocsJar)
            artifact(tasks.named("kotlinSourcesJar"))
            pom {
                name.set("Stacktrace-decoroutinator JVM.")
                description.set("JVM library for recovering stack trace in exceptions thrown in Kotlin coroutines.")
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
