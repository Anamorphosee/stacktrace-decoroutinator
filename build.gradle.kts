buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${versions["kotlin"]}")
        classpath("com.github.johnrengelman:shadow:${versions["shadow"]}")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:${versions["dokka"]}")
        classpath("com.android.tools.build:gradle:${versions["androidGradle"]}")
        classpath("com.gradle.publish:plugin-publish-plugin:${versions["pluginPublish"]}")
        classpath("dev.reformator.bytecodeprocessor:bytecode-processor-plugins")
        classpath("dev.reformator.bytecodeprocessor:bytecode-processor-intrinsics")
        classpath("dev.reformator.decoroutinatortest:decoroutinatortest")
    }
}

subprojects {
    group = "dev.reformator.stacktracedecoroutinator"
    version = "2.4.6"
}
