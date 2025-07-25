import dev.reformator.bytecodeprocessor.plugins.ChangeClassNameProcessor
import dev.reformator.bytecodeprocessor.plugins.GetOwnerClassProcessor
import dev.reformator.bytecodeprocessor.plugins.LoadConstantProcessor
import dev.reformator.bytecodeprocessor.plugins.RemoveModuleRequiresProcessor
import org.gradle.kotlin.dsl.named
import org.jetbrains.dokka.gradle.AbstractDokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.util.Base64
import kotlin.jvm.java

plugins {
    kotlin("jvm")
    alias(libs.plugins.dokka)
    alias(libs.plugins.nmcp)
    `maven-publish`
    signing
    id("dev.reformator.bytecodeprocessor")
    id("dev.reformator.forcevariantjavaversion")
}

repositories {
    mavenCentral()
}

dependencies {
    //noinspection UseTomlInstead
    compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")
    compileOnly(project(":intrinsics"))

    implementation(project(":stacktrace-decoroutinator-provider"))
    implementation(project(":stacktrace-decoroutinator-runtime-settings"))
    implementation(project(":stacktrace-decoroutinator-common"))
    implementation(project(":stacktrace-decoroutinator-generator"))
    implementation(libs.asm.utils)

    runtimeOnly(project(":stacktrace-decoroutinator-mh-invoker"))

    testImplementation(kotlin("test"))
}

bytecodeProcessor {
    processors = setOf(
        GetOwnerClassProcessor(setOf(GetOwnerClassProcessor.MethodKey(
            className = "dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorProviderApiKt",
            methodName = "getProviderApiClass"
        ))),
        RemoveModuleRequiresProcessor("dev.reformator.bytecodeprocessor.intrinsics", "intrinsics"),
        ChangeClassNameProcessor(mapOf(
            "dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation" to "kotlin.coroutines.jvm.internal.BaseContinuationImpl"
        )),
    )
}

val fillConstantProcessorTask = tasks.register("fillConstantProcessor") {
    val accessorProject = project(":base-continuation-accessor")
    val accessorCompileKotlinTask = accessorProject.tasks.named<KotlinJvmCompile>("compileKotlin")
    dependsOn(accessorCompileKotlinTask)
    doLast {
        val regularAccessorClass = accessorCompileKotlinTask.get().destinationDirectory.get().dir("kotlin")
            .dir("coroutines").dir("jvm").dir("internal")
            .file("DecoroutinatorBaseContinuationAccessorImpl.class").asFile
        val regularAccessorClassBodyBase64 = Base64.getEncoder().encodeToString(regularAccessorClass.readBytes())
        bytecodeProcessor {
            processors += LoadConstantProcessor(mapOf(
                LoadConstantProcessor.Key(
                    "dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal.Base_continuation_accessor_agent_commonKt",
                    "getRegularAccessorBodyBase64"
                ) to LoadConstantProcessor.Value(regularAccessorClassBodyBase64)
            ))
        }
    }
}

tasks.withType(KotlinJvmCompile::class.java) {
    dependsOn(fillConstantProcessorTask)
}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_9
    targetCompatibility = JavaVersion.VERSION_1_9
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:-module"))
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

val dokkaJavadocsJar = tasks.register<Jar>("dokkaJavadocsJar") {
    val dokkaJavadocTask = tasks.named<AbstractDokkaTask>("dokkaJavadoc").get()
    dependsOn(dokkaJavadocTask)
    archiveClassifier.set("javadoc")
    from(dokkaJavadocTask.outputDirectory)
}

val mavenPublicationName = "maven"

publishing {
    publications {
        create<MavenPublication>(mavenPublicationName) {
            from(components["java"])
            artifact(dokkaJavadocsJar)
            artifact(tasks.named("kotlinSourcesJar"))
            pom {
                name.set("Stacktrace-decoroutinator JVM agent common lib.")
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
            name = "snapshot"
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
            credentials {
                username = properties["sonatype.username"] as String?
                password = properties["sonatype.password"] as String?
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications[mavenPublicationName])
}

nmcp {
    publish(mavenPublicationName) {}
}
