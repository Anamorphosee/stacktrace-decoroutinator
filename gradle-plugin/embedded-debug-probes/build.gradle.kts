import dev.reformator.bytecodeprocessor.plugins.AddToStaticInitializerProcessor
import dev.reformator.bytecodeprocessor.plugins.ChangeClassNameProcessor
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("dev.reformator.bytecodeprocessor")
}

bytecodeProcessor {
    processors = setOf(
        ChangeClassNameProcessor(),
        AddToStaticInitializerProcessor
    )
}

repositories {
    mavenCentral()
}

dependencies {
    //noinspection UseTomlInstead
    compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")
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
    test {
        kotlin.destinationDirectory = java.destinationDirectory
    }
}
