import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.*

plugins {
    val fileInputStreamClass = Class.forName("java.io.FileInputStream")
    val inputStreamClass = Class.forName("java.io.InputStream")
    val propertiesClass = Class.forName("java.util.Properties")
    val fileInputSteamConstructor = fileInputStreamClass.getDeclaredConstructor(String::class.java)

    @Suppress("UNCHECKED_CAST")
    fun tryLoadVersions(path: String): Map<String, String>? =
        (try {
            fileInputSteamConstructor.newInstance("$path/versions.properties")
        } catch (_: Exception) {
            null
        } as AutoCloseable?)?.use { input ->
            val properties = propertiesClass.getDeclaredConstructor().newInstance()
            propertiesClass.getDeclaredMethod("load", inputStreamClass).invoke(properties, input)
            properties as Map<String, String>
        }

    val decoroutinatorVersions = tryLoadVersions(".") ?: tryLoadVersions("..")!!

    `java-gradle-plugin`
    kotlin("jvm") version decoroutinatorVersions["kotlin"]
}

val decoroutinatorVersions = FileInputStream(projectDir.resolve("../versions.properties")).use { input ->
    val properties = Properties()
    properties.load(input)
    properties.mapKeys { (key, _) -> key.toString() }.mapValues { (_, value) -> value.toString() }
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api:${decoroutinatorVersions["kotlin"]}")
    implementation("org.ow2.asm:asm-util:${decoroutinatorVersions["asm"]}")
    implementation("io.github.microutils:kotlin-logging-jvm:${decoroutinatorVersions["kotlinLoggingJvm"]}")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    modularity.inferModulePath = false
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
        freeCompilerArgs.add("-Xallow-kotlin-package")
    }
}

val kotlinSources = sourceSets.main.get().kotlin
//javaSources.srcDirs("../runtime/src/main/java")
//kotlinSources.srcDirs("../runtime/src/main/kotlin")
//javaSources.srcDirs("../generator/src/main/java")
//kotlinSources.srcDirs("../generator/src/main/kotlin")
//kotlinSources.srcDirs("../gradle-plugin/src/main/kotlin")

//tasks.named("classes") {
//    doLast {
//        val classesPath = layout.buildDirectory.get()
//            .dir("classes")
//            .dir("kotlin")
//            .dir("main")
//        val baseContinuationDirPath = classesPath
//            .dir("kotlin")
//            .dir("coroutines")
//            .dir("jvm")
//            .dir("internal")
//        val baseContinuationClassFileName = "BaseContinuationImpl.class"
//        copy {
//            from(baseContinuationDirPath)
//            into(classesPath)
//            include(baseContinuationClassFileName)
//            rename(
//                baseContinuationClassFileName,
//                "dev.reformator.stacktracedecoroutinator.decoroutinatorBaseContinuation.class"
//            )
//        }
//        delete(baseContinuationDirPath.file(baseContinuationClassFileName))
//    }
//}

gradlePlugin {
    plugins {
//        create("decoroutinatorPlugin") {
//            id = "dev.reformator.stacktracedecoroutinator"
//            implementationClass = "dev.reformator.stacktracedecoroutinator.gradleplugin.DecoroutinatorPlugin"
//        }
    }
}
