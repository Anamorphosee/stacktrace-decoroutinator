import org.gradle.kotlin.dsl.versions
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("com.gradle.plugin-publish")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api:${versions["kotlin"]}")
    implementation(project(":bytecode-processor-plugin-api"))
    implementation(project(":bytecode-processor-impl"))
    testImplementation(kotlin("test"))
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

gradlePlugin {
    plugins {
        create("bytecodeProcessor") {
            id = "dev.reformator.bytecodeprocessor"
            implementationClass = "dev.reformator.bytecodeprocessor.gradleplugin.BytecodeProcessorPlugin"
        }
    }
}
