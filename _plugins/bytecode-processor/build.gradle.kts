buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${versions["kotlin"]}")
        classpath("com.gradle.publish:plugin-publish-plugin:${versions["pluginPublish"]}")
    }
}

subprojects {
    group = "dev.reformator.bytecodeprocessor"
    version = "0.0.1-SNAPSHOT"
}
