import dev.reformator.bytecodeprocessor.plugins.*
import org.gradle.kotlin.dsl.versions
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    kotlin("jvm") version versions["kotlin"]
    id("com.gradle.plugin-publish") version versions["pluginPublish"]
    id("dev.reformator.bytecodeprocessor")
}

group = "dev.reformator.decoroutinatortest"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    compileOnly("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")

    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api:${versions["kotlin"]}")
    implementation("org.ow2.asm:asm-util:${versions["asm"]}")
    implementation("io.github.oshai:kotlin-logging-jvm:${versions["kotlinLoggingJvm"]}")
}

bytecodeProcessor {
    processors = setOf(
        GetOwnerClassProcessor(),
        ChangeClassNameProcessor(),
        ChangeInvocationsOwnerProcessor,
        SkipInvocationsProcessor,
        MakeStaticProcessor,
        RemoveKotlinStdlibProcessor(includeClassNames = setOf(
            Regex("dev.reformator.stacktracedecoroutinator.generator.internal.GeneratorSpecImpl")
        )),
        LoadConstantProcessor(mapOf(
            LoadConstantProcessor.Key(
                "dev.reformator.stacktracedecoroutinator.gradleplugin.CommonGradlePluginKt",
                "getProjectVersionIntrinsic"
            ) to LoadConstantProcessor.Value("unspecified"),
            LoadConstantProcessor.Key(
                "dev.reformator.stacktracedecoroutinator.generator.internal.ClassLoaderGeneratorKt",
                "getIsolatedSpecClassName"
            ) to LoadConstantProcessor.Value(""),
            LoadConstantProcessor.Key(
                "dev.reformator.stacktracedecoroutinator.generator.internal.ClassLoaderGeneratorKt",
                "getIsolatedSpecClassBodyBase64"
            ) to LoadConstantProcessor.Value("")
        ))
    )
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

val kotlinSources = sourceSets.main.get().kotlin
val resourcesSources = sourceSets.main.get().resources
kotlinSources.srcDirs("../../provider/src/main/kotlin")
kotlinSources.srcDirs("../../intrinsics/src/main/kotlin")
kotlinSources.srcDirs("../../common/src/main/kotlin")
resourcesSources.srcDirs("../../common/src/main/resources")
kotlinSources.srcDirs("../../generator/src/main/kotlin")
resourcesSources.srcDirs("../../generator/src/main/resources")
kotlinSources.srcDirs("../../gradle-plugin/src/main/kotlin")


gradlePlugin {
    plugins {
        create("decoroutinatorPlugin") {
            id = "dev.reformator.stacktracedecoroutinator"
            implementationClass = "dev.reformator.stacktracedecoroutinator.gradleplugin.DecoroutinatorPlugin"
        }
    }
}
