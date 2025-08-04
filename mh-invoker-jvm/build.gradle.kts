import dev.reformator.bytecodeprocessor.impl.applyBytecodeProcessors
import dev.reformator.bytecodeprocessor.plugins.*
import dev.reformator.stacktracedecoroutinator.common.internal.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.generator.internal.transformClassBody
import org.apache.commons.io.output.ByteArrayOutputStream
import org.gradle.kotlin.dsl.named
import org.jetbrains.dokka.gradle.AbstractDokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.util.Base64
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.apply
import kotlin.sequences.forEach

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

val transformedAttribute = Attribute.of(
    "transformed",
    Boolean::class.javaObjectType
)

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

    //noinspection UseTomlInstead
    compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")
    compileOnly(project(":intrinsics"))

    implementation(project(":stacktrace-decoroutinator-provider"))
    implementation(project(":stacktrace-decoroutinator-common"))

    testImplementation(kotlin("test"))
    testImplementation(project(":stacktrace-decoroutinator-provider"))
    testImplementation(project(":test-utils"))
    testImplementation(project(":test-utils-jvm"))
    testRuntimeOnly(project(":stacktrace-decoroutinator-generator"))
    testRuntimeOnly(project(":test-utils:base-continuation-accessor-stub"))
}

afterEvaluate {
    configurations.testRuntimeClasspath.get().attributes.attribute(transformedAttribute, true)
}

bytecodeProcessor {
    processors = setOf(
        RemoveModuleRequiresProcessor("dev.reformator.bytecodeprocessor.intrinsics", "intrinsics"),
        ChangeClassNameProcessor(mapOf(
            "dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation" to "kotlin.coroutines.jvm.internal.BaseContinuationImpl"
        )),
        ChangeInvocationsOwnerProcessor,
        GetOwnerClassProcessor()
    )
}

private fun File.clearDir() {
    listFiles().forEach {
        if (it.isDirectory) {
            it.deleteRecursively()
        } else {
            it.delete()
        }
    }
}

private val File.isClass: Boolean
    get() = isFile && name.endsWith(".class") && name != "module-info.class"

private fun File.copyClassesTo(output: File) {
    walk().filter { it.isClass }.forEach { file ->
        val outputFile = output.resolve(file.relativeTo(this))
        outputFile.parentFile.mkdirs()
        file.copyTo(outputFile)
    }
}

private val File.classNameSequence: Sequence<String>
    get() = walk().filter { it.isClass }.map {
        it.relativeTo(this).path.removeSuffix(".class").replace(File.separator, ".")
    }

private fun File.zipDirectoryToArray(): ByteArray {
    val bufferOutput = ByteArrayOutputStream()
    ZipOutputStream(bufferOutput).use { output ->
        walk().forEach { file ->
            val entryName = file.toRelativeString(this).replace(File.separator, "/")
            if (file.isDirectory) {
                output.putNextEntry(ZipEntry("$entryName/").also {
                    it.method = ZipEntry.DEFLATED
                })
            } else {
                val buffer = file.readBytes()
                output.putNextEntry(ZipEntry(entryName).also {
                    it.method = ZipEntry.DEFLATED
                    it.size = buffer.size.toLong()
                })
                output.write(buffer)
            }
        }
    }
    return bufferOutput.toByteArray()
}

val fillConstantProcessorTask = tasks.register("fillConstantProcessor") {
    val mhInvokerProject = project(":stacktrace-decoroutinator-mh-invoker")
    val mhInvokerCompileKotlinTask = mhInvokerProject.tasks.named<KotlinJvmCompile>("compileKotlin")
    dependsOn(mhInvokerCompileKotlinTask)
    doLast {
        val tempDir = temporaryDir
        tempDir.clearDir()
        mhInvokerCompileKotlinTask.get().destinationDirectory.get().asFile.copyClassesTo(tempDir)
        val packageNames = listOf("dev.reformator.stacktracedecoroutinator.mhinvoker", "dcunknown")
        val changeClassNameParameters = tempDir.classNameSequence.map { className ->
            val packageName = packageNames.first { className.startsWith(it) }
            val newClassName = "${packageName}jvm${className.removePrefix(packageName)}"
            className to newClassName
        }.toMap()
        applyBytecodeProcessors(
            processors = setOf(ChangeClassNameProcessor(changeClassNameParameters)),
            classesDir = tempDir
        )
        val jarBodyBase64 = Base64.getEncoder().encodeToString(tempDir.zipDirectoryToArray())
        bytecodeProcessor {
            processors += LoadConstantProcessor(mapOf(
                LoadConstantProcessor.Key(
                    "dev.reformator.stacktracedecoroutinator.mhinvokerjvm.internal.MhInvokerJvmKt",
                    "getRegularMethodHandleJarBase64"
                ) to LoadConstantProcessor.Value(jarBodyBase64)
            ))
        }
    }
}

tasks.withType(KotlinJvmCompile::class.java) {
    dependsOn(fillConstantProcessorTask)
}

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
                                    skipSpecMethods = false,
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
                name.set("Stack Trace Decoroutinator MethodHandle JVM invoker.")
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
