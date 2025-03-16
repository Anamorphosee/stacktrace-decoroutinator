import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    alias(libs.plugins.gradle.plugin.publish)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.gradle.plugin.api)
    implementation(project(":bytecode-processor-plugin-api"))
    implementation(project(":bytecode-processor-impl"))
    implementation(project(":bytecode-processor-plugins"))
    implementation(project(":bytecode-processor-intrinsics"))
    implementation(libs.asm.utils)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:-module"))
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

gradlePlugin {
    plugins {
        create("bytecodeProcessor") {
            id = "dev.reformator.bytecodeprocessor"
            implementationClass = "dev.reformator.bytecodeprocessor.gradleplugin.BytecodeProcessorPlugin"
        }
    }
}
