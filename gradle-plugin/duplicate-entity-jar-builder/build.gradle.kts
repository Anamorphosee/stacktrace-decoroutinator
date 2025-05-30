import dev.reformator.bytecodeprocessor.plugins.GetCurrentFileNameProcessor
import dev.reformator.bytecodeprocessor.plugins.GetCurrentLineNumberProcessor
import dev.reformator.bytecodeprocessor.plugins.GetOwnerClassProcessor
import dev.reformator.bytecodeprocessor.plugins.RemoveModuleRequiresProcessor
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

plugins {
    kotlin("jvm")
    id("dev.reformator.bytecodeprocessor")
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")
}

bytecodeProcessor {
    processors = setOf(
        GetCurrentLineNumberProcessor,
        GetCurrentFileNameProcessor,
        GetOwnerClassProcessor(),
        RemoveModuleRequiresProcessor("dev.reformator.bytecodeprocessor.intrinsics"),
    )
}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_9
    targetCompatibility = JavaVersion.VERSION_1_9
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

val duplicateJarConfig = configurations.create("duplicateJar")

val createDuplicateJarTask = tasks.register<DefaultTask>("createDuplicateJar") {
    val compileJavaTask = tasks.named<AbstractCompile>("compileJava")
    val compileKotlinTask = tasks.named<KotlinJvmCompile>("compileKotlin")
    dependsOn(compileJavaTask, compileKotlinTask)
    outputs.file(layout.buildDirectory.file("duplicate-entities.jar"))
    doLast {
        ZipOutputStream(FileOutputStream(outputs.files.singleFile)).use { output ->
            output.putDirectoryDuplicate(compileJavaTask.get().destinationDirectory.asFile.get())
            output.putDirectoryDuplicate(compileKotlinTask.get().destinationDirectory.asFile.get())
        }
    }
}

artifacts.add(duplicateJarConfig.name, createDuplicateJarTask)

fun ZipOutputStream.putDirectoryDuplicate(root: File) {
    root.walk().forEach { file ->
        val path = file.relativeTo(root).path.replace(File.pathSeparatorChar, '/')
        if (file.isDirectory) {
            val dirPath = "$path/"
            putEntry(dirPath)
            putEntry(dirPath)
        } else {
            val buffer = file.readBytes()
            putEntry(path)
            write(buffer)
            putEntry(path)
            write(buffer)
        }
    }
}

private fun ZipOutputStream.putEntry(name: String) {
    putNextEntry(ZipEntry(name).apply {
        method = ZipEntry.DEFLATED
    })
    val namesField = ZipOutputStream::class.java.getDeclaredField("names").apply {
        isAccessible = true
    }
    (namesField.get(this) as HashSet<*>).clear()
}
