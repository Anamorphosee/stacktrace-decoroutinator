//import dev.reformator.stacktracedecoroutinator.generator.internal.loadDecoroutinatorBaseContinuationClassBody
//import dev.reformator.stacktracedecoroutinator.runtime.internal.BASE_CONTINUATION_CLASS_NAME
import org.jetbrains.dokka.gradle.AbstractDokkaTask
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

plugins {
    id("com.android.library")
    kotlin("android")
    id("org.jetbrains.dokka")
    `maven-publish`
    signing
}

repositories {
    mavenCentral()
    google()
}

android {
    namespace = "dev.reformator.stacktracedecoroutinator.generatorruntimeandroidtests"
    compileSdk = 26
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    packaging {
        resources.pickFirsts.add("META-INF/*")
    }
    kotlin {
        jvmToolchain(8)
    }
}

val transformedAttribute = Attribute.of(
    "transformed",
    Boolean::class.javaObjectType
)

//abstract class Transform: TransformAction<TransformParameters.None> {
//    @get:InputArtifact
//    abstract val inputArtifact: Provider<FileSystemLocation>
//
//    override fun transform(outputs: TransformOutputs) {
//        val file = inputArtifact.get().asFile
//        if (file.name.startsWith("kotlin-stdlib-") && file.extension == "jar") {
//            JarOutputStream(outputs.file("kotlin-stdlib-transformed.jar").outputStream()).use { output ->
//                JarFile(file).use { input ->
//                    input.entries().asSequence().forEach { entry ->
//                        output.putNextEntry(ZipEntry(entry.name).apply {
//                            method = ZipEntry.DEFLATED
//                        })
//                        if (entry.name == BASE_CONTINUATION_CLASS_NAME.replace('.', '/') + ".class") {
//                            output.write(loadDecoroutinatorBaseContinuationClassBody())
//                        } else if (!entry.isDirectory) {
//                            input.getInputStream(entry).use { it.copyTo(output) }
//                        }
//                        output.closeEntry()
//                    }
//                }
//            }
//        } else {
//            outputs.file(inputArtifact)
//        }
//    }
//}


dependencies {
    attributesSchema.attribute(transformedAttribute)
//    artifactTypes.getByName("jar", object: Action<ArtifactTypeDefinition> {
//        override fun execute(t: ArtifactTypeDefinition) {
//            t.attributes.attribute(transformedAttribute, false)
//        }
//    })
//    registerTransform(Transform::class.java, object: Action<TransformSpec<TransformParameters.None>> {
//        override fun execute(t: TransformSpec<TransformParameters.None>) {
//            t.from.attribute(transformedAttribute, false)
//            t.to.attribute(transformedAttribute, true)
//        }
//    })

    compileOnly(project(":isolated-spec-class"))

    implementation(project(":stacktrace-decoroutinator-common"))
    implementation("com.jakewharton.android.repackaged:dalvik-dx:${decoroutinatorVersions["dalvikDx"]}")

    androidTestRuntimeOnly(project(":test-utils"))
    androidTestRuntimeOnly("androidx.test:runner:${decoroutinatorVersions["androidTestRunner"]}")
}

afterEvaluate {
    //configurations["debugAndroidTestRuntimeClasspath"].attributes.attribute(transformedAttribute, true)
}

val dokkaJavadocsJar = task("dokkaJavadocsJar", Jar::class) {
    val dokkaJavadocTask = tasks.named<AbstractDokkaTask>("dokkaJavadoc").get()
    dependsOn(dokkaJavadocTask)
    archiveClassifier.set("javadoc")
    from(dokkaJavadocTask.outputDirectory)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["release"])
                artifact(dokkaJavadocsJar)
                pom {
                    name.set("Stacktrace-decoroutinator Android runtime class generator.")
                    description.set("Android library for recovering stack trace in exceptions thrown in Kotlin coroutines.")
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
}
