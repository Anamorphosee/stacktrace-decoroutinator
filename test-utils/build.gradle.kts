import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.reformator.bytecodeprocessor.plugins.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("jvm")
    id("dev.reformator.bytecodeprocessor")
    id("dev.reformator.forcevariantjavaversion")
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")

    implementation(project(":stacktrace-decoroutinator-common"))
    implementation("org.junit.jupiter:junit-jupiter-api:${versions["junit5"]}")
    implementation("junit:junit:${versions["junit4"]}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${versions["kotlinxCoroutines"]}")
    implementation("io.github.oshai:kotlin-logging-jvm:${versions["kotlinLoggingJvm"]}")

    runtimeOnly("io.ktor:ktor-io-jvm:${versions["ktor"]}")
    runtimeOnly("ch.qos.logback:logback-classic:${versions["logbackClassic"]}")
}

bytecodeProcessor {
    processors = setOf(
        RemoveModuleRequiresProcessor("dev.reformator.bytecodeprocessor.intrinsics", "intrinsics"),
        GetCurrentFileNameProcessor,
        GetCurrentLineNumberProcessor,
        GetOwnerClassProcessor()
    )
}

val fillConstantProcessorTask: Task = tasks.create("fillConstantProcessor") {
    val customLoaderProject = project(":test-utils:custom-loader")
    customLoaderProject.afterEvaluate {
        val customLoaderJarTask = customLoaderProject.tasks.named<ShadowJar>("shadowJar")
        dependsOn(customLoaderJarTask)
    }
    doLast {
        val customLoaderJarTask = customLoaderProject.tasks.named<ShadowJar>("shadowJar")
        bytecodeProcessor {
            processors += LoadConstantProcessor(mapOf(
                LoadConstantProcessor.Key(
                    "dev.reformator.stacktracedecoroutinator.test.Runtime_testKt",
                    "getCustomLoaderJarUri"
                ) to LoadConstantProcessor.Value(
                    customLoaderJarTask.get().archiveFile.get().asFile.toURI().toString()
                )
            ))
        }
    }
}

tasks.withType(KotlinJvmCompile::class.java) {
    dependsOn(fillConstantProcessorTask)
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
