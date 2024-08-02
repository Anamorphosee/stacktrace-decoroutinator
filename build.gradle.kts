buildscript {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${decoroutinatorVersions["kotlin"]}")
        classpath("com.github.johnrengelman:shadow:${decoroutinatorVersions["shadow"]}")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:${decoroutinatorVersions["dokka"]}")
        classpath("com.android.tools.build:gradle:${decoroutinatorVersions["androidGradle"]}")
    }
}

subprojects {
    group = "dev.reformator.stacktracedecoroutinator"
    version = "2.4.0"
}
