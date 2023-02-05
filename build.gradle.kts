buildscript {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.0.4")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.22")
        classpath("gradle.plugin.com.github.johnrengelman:shadow:7.1.2")
    }
}

subprojects {
    group = "dev.reformator.stacktracedecoroutinator"
    version = "2.3.5"
}
