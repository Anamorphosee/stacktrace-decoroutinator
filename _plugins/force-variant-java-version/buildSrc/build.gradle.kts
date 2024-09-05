import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

    val versions = tryLoadVersions(".") ?: tryLoadVersions("..") ?: tryLoadVersions("../..") ?: tryLoadVersions("../../..")!!

    kotlin("jvm") version versions["kotlin"]
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}
