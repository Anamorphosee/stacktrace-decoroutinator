//import dev.reformator.stacktracedecoroutinator.generator.internal.loadDecoroutinatorBaseContinuationClassBody
//import dev.reformator.stacktracedecoroutinator.runtime.internal.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.bytecodeprocessor.plugins.*
import org.jetbrains.dokka.gradle.AbstractDokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.io.InputStream
import java.lang.invoke.MethodHandles
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
    `maven-publish`
    signing
    id("dev.reformator.bytecodeprocessor")
}

repositories {
    mavenCentral()
}

val transformedAttribute = Attribute.of(
    "transformed",
    Boolean::class.javaObjectType
)

abstract class Transform: TransformAction<TransformParameters.None> {
    companion object {
        private const val PROVIDER_API_CLASS_INTERNAL_NAME = "dev/reformator/stacktracedecoroutinator/provider/DecoroutinatorProviderApiKt"
        private const val BASE_CONTINUATION_CLASS_NAME = "kotlin.coroutines.jvm.internal.BaseContinuationImpl"
        private fun getClassNode(classBody: InputStream): ClassNode {
            val classReader = ClassReader(classBody)
            val classNode = ClassNode(Opcodes.ASM9)
            classReader.accept(classNode, 0)
            return classNode
        }
        private val ClassNode.classBody: ByteArray
            get() {
                val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
                accept(writer)
                return writer.toByteArray()
            }
        private fun transformBaseContinuation(baseContinuation: ClassNode) {
            val resumeWithMethod = baseContinuation.methods?.find { it.name == "resumeWith" }!!
            resumeWithMethod.instructions.insertBefore(resumeWithMethod.instructions.first, InsnList().apply {
                add(MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    PROVIDER_API_CLASS_INTERNAL_NAME,
                    "isDecoroutinatorEnabled",
                    "()${Type.BOOLEAN_TYPE.descriptor}"
                ))
                val defaultAwakeLabel = LabelNode()
                add(JumpInsnNode(
                    Opcodes.IFEQ,
                    defaultAwakeLabel
                ))
                add(MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    PROVIDER_API_CLASS_INTERNAL_NAME,
                    "isBaseContinuationPrepared",
                    "()${Type.BOOLEAN_TYPE.descriptor}"
                ))
                val decoroutinatorAwakeLabel = LabelNode()
                add(JumpInsnNode(
                    Opcodes.IFNE,
                    decoroutinatorAwakeLabel
                ))
                add(MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(MethodHandles::class.java),
                    MethodHandles::lookup.name,
                    "()${Type.getDescriptor(MethodHandles.Lookup::class.java)}"
                ))
                add(MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    PROVIDER_API_CLASS_INTERNAL_NAME,
                    "prepareBaseContinuation",
                    "(${Type.getDescriptor(MethodHandles.Lookup::class.java)})${Type.VOID_TYPE.descriptor}"
                ))
                add(decoroutinatorAwakeLabel)
                add(FrameNode(Opcodes.F_SAME, 0, null, 0, null))
                add(VarInsnNode(Opcodes.ALOAD, 0))
                add(VarInsnNode(Opcodes.ALOAD, 1))
                add(MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    PROVIDER_API_CLASS_INTERNAL_NAME,
                    "awakeBaseContinuation",
                    "(${Type.getDescriptor(Object::class.java)}${Type.getDescriptor(Object::class.java)})${Type.VOID_TYPE.descriptor}"
                ))
                add(InsnNode(Opcodes.RETURN))
                add(defaultAwakeLabel)
                add(FrameNode(Opcodes.F_SAME, 0, null, 0, null))
            })
        }
    }

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
                            val clazz = input.getInputStream(entry).use { getClassNode(it) }
                            transformBaseContinuation(clazz)
                            output.write(clazz.classBody)
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
    implementation("org.ow2.asm:asm-util:${decoroutinatorVersions["asm"]}")

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
        GetOwnerClassProcessor(setOf(
            GetOwnerClassProcessor.MethodKey(
                className = "dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorProviderApiKt",
                methodName = "getProviderApiClass"
        )))
    )
}

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
