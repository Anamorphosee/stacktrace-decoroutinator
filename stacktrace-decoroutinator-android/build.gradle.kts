plugins {
    id("com.android.library")
    id("kotlin-android")
    `maven-publish`
    signing
}

android {
    compileSdk = 26

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(project(":stacktrace-decoroutinator-common"))
    implementation("com.jakewharton.android.repackaged:dalvik-dx:${properties["dalvikDxVersion"]}")

    testImplementation("junit:junit:4.+")

    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${properties["kotlinxCoroutinesVersion"]}")
}

val generateBaseContinuationDexTask = task("generateBaseContinuationDex") {
    val folder = "$projectDir/src/main/resources"
    doLast {
        exec {
            file(folder).mkdir()
            setCommandLine(
                "${android.sdkDirectory}/build-tools/${android.buildToolsVersion}/d8",
                "--min-api", "26",
                "--output", folder,
                "${project(":stacktrace-decoroutinator-common").buildDir}/classes/kotlin/main/kotlin/coroutines/jvm/internal/BaseContinuationImpl.class"
            )
        }
        copy {
            from(folder)
            into(folder)
            include("classes.dex")
            rename("classes.dex", "decoroutinatorBaseContinuation.dex")
        }
        delete("$folder/classes.dex")
    }
    dependsOn(":stacktrace-decoroutinator-common:compileKotlin")
}

tasks.named("preBuild") {
    dependsOn(generateBaseContinuationDexTask)
}

val androidJavadocs = task("androidJavadocs", Javadoc::class) {
    setSource(android.sourceSets["main"].java.srcDirs)
    classpath += files(*android.bootClasspath.toTypedArray())
    android.libraryVariants.asSequence()
        .filter { it.name == "release" }
        .forEach {
            classpath += it.javaCompileProvider.get().classpath
        }
    exclude("**/R.html", "**/R.*.html", "**/index.html")
}

val androidJavadocsJar = task("androidJavadocsJar", Jar::class) {
    dependsOn(androidJavadocs)
    archiveClassifier.set("javadoc")
    from(androidJavadocs.destinationDir)
}

val androidSourcesJar = task("androidSourcesJar", Jar::class) {
    archiveClassifier.set("sources")
    from(android.sourceSets["main"].java.srcDirs)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["release"])
                artifact(androidJavadocsJar)
                artifact(androidSourcesJar)
                pom {
                    name.set("Stacktrace-decoroutinator")
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
            maven {
                name = "github"
                url = uri("https://maven.pkg.github.com/Anamorphosee/stacktrace-decoroutinator")
                credentials {
                    username = properties["github.username"] as String?
                    password = properties["github.password"] as String?
                }
            }
        }
    }

    signing {
        useGpgCmd()
        sign(publishing.publications["maven"])
    }
}
