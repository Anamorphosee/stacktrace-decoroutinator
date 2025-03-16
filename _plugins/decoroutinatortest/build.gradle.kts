import dev.reformator.bytecodeprocessor.plugins.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm.version)
    alias(libs.plugins.gradle.plugin.publish)
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

    implementation(libs.kotlin.gradle.plugin.api)
    implementation(libs.asm.utils)
    implementation(libs.kotlin.logging.jvm)
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
                "org.gradle.kotlin.dsl.ApiGradlePluginDecoroutinatorKt",
                "getProjectVersionIntrinsic"
            ) to LoadConstantProcessor.Value("unspecified")
        ))
    )
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
