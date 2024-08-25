buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${decoroutinatorVersions["kotlin"]}")
        classpath("com.github.johnrengelman:shadow:${decoroutinatorVersions["shadow"]}")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:${decoroutinatorVersions["dokka"]}")
        classpath("com.android.tools.build:gradle:${decoroutinatorVersions["androidGradle"]}")
        classpath("com.gradle.publish:plugin-publish-plugin:${decoroutinatorVersions["pluginPublish"]}")
        classpath("dev.reformator.bytecodeprocessor:bytecode-processor-plugins")
        classpath("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")
    }
}

subprojects {
    group = "dev.reformator.stacktracedecoroutinator"
    version = "2.4.2"
}
