import dev.reformator.stacktracedecoroutinator.common.internal.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.bytecodeprocessor.plugins.*
import dev.reformator.stacktracedecoroutinator.generator.internal.addReadProviderModuleToModuleInfo
import dev.reformator.stacktracedecoroutinator.generator.internal.transformClassBody
import org.jetbrains.dokka.gradle.AbstractDokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.Base64

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
    `maven-publish`
    signing
    id("dev.reformator.bytecodeprocessor")
    id("dev.reformator.forcevariantjavaversion")
}

repositories {
    mavenCentral()
}

val transformedAttribute = Attribute.of(
    "transformed",
    Boolean::class.javaObjectType
)

abstract class Transform: TransformAction<TransformParameters.None> {
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val file = inputArtifact.get().asFile
        if (file.name.startsWith("kotlin-stdlib-") && file.extension == "jar") {
            JarOutputStream(outputs.file("kotlin-stdlib-transformed.jar").outputStream()).use { output ->
                JarFile(file).use { input ->
                    input.entries().asSequence().forEach { entry ->
                        output.putNextEntry(ZipEntry(entry.name).apply {
                            method = ZipEntry.DEFLATED
                        })
                        if (entry.name == BASE_CONTINUATION_CLASS_NAME.replace('.', '/') + ".class") {
                            output.write(input.getInputStream(entry).use {
                                transformClassBody(
                                    classBody = it,
                                    metadataResolver = { error("no need") }
                                ).updatedBody!!
                            })
                        } else if (entry.name.endsWith("/module-info.class")) {
                            output.write(input.getInputStream(entry).use {
                                addReadProviderModuleToModuleInfo(it)!!
                            })
                        } else if (!entry.isDirectory) {
                            input.getInputStream(entry).use { it.copyTo(output) }
                        }
                        output.closeEntry()
                    }
                }
            }
        } else {
            outputs.file(inputArtifact)
        }
    }
}

dependencies {
    attributesSchema.attribute(transformedAttribute)
    artifactTypes.getByName("jar", object: Action<ArtifactTypeDefinition> {
        override fun execute(t: ArtifactTypeDefinition) {
            t.attributes.attribute(transformedAttribute, false)
        }
    })
    registerTransform(Transform::class.java, object: Action<TransformSpec<TransformParameters.None>> {
        override fun execute(t: TransformSpec<TransformParameters.None>) {
            t.from.attribute(transformedAttribute, false)
            t.to.attribute(transformedAttribute, true)
        }
    })

    compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")
    compileOnly(project(":intrinsics"))

    implementation(project(":stacktrace-decoroutinator-provider"))
    implementation(project(":stacktrace-decoroutinator-common"))
    implementation("org.ow2.asm:asm-util:${versions["asm"]}")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("script-runtime"))
    testImplementation(project(":test-utils"))
}

bytecodeProcessor {
    processors = setOf(
        RemoveModuleRequiresProcessor("dev.reformator.bytecodeprocessor.intrinsics", "intrinsics"),
        ChangeClassNameProcessor(mapOf(
            "dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation" to "kotlin.coroutines.jvm.internal.BaseContinuationImpl"
        )),
        MakeStaticProcessor,
        GetOwnerClassProcessor(setOf(GetOwnerClassProcessor.MethodKey(
            className = "dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorProviderApiKt",
            methodName = "getProviderApiClass"
        )))
    )
}

val setupIsolatedSpecProcessorTask = tasks.create("setupIsolatedSpecProcessor") {
    dependsOn(":isolated-spec-class:classes")
    doLast {
        val classesDir = project(":isolated-spec-class").layout.buildDirectory.get()
            .dir("classes")
            .dir("java")
            .dir("main")
            .asFile
        val classFile =
            classesDir.walk().find { it.isFile && it.extension == "class" && it.name != "module-info.class" }!!
        val className = classFile.relativeTo(classesDir).path.removeSuffix(".class").replace(File.separator, ".")
        val classBodyBase64 = Base64.getEncoder().encodeToString(classFile.readBytes())
        bytecodeProcessor {
            processors += setOf(
                LoadConstantProcessor(
                    mapOf(
                        LoadConstantProcessor.Key(
                            "dev.reformator.stacktracedecoroutinator.generator.internal.ClassLoaderGeneratorKt",
                            "getIsolatedSpecClassName"
                        ) to LoadConstantProcessor.Value(className),
                        LoadConstantProcessor.Key(
                            "dev.reformator.stacktracedecoroutinator.generator.internal.ClassLoaderGeneratorKt",
                            "getIsolatedSpecClassBodyBase64"
                        ) to LoadConstantProcessor.Value(classBodyBase64)
                    )
                )
            )
        }
    }
}

tasks.named("compileKotlin").get().dependsOn(setupIsolatedSpecProcessorTask)

afterEvaluate {
    configurations.testRuntimeClasspath.get().attributes.attribute(transformedAttribute, true)
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
                name.set("Stacktrace-decoroutinator JVM common lib.")
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

signing {
    useGpgCmd()
    sign(publishing.publications["maven"])
}
