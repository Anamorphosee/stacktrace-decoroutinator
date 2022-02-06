buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.0.3")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.0")
    }
}

subprojects {
    group = "dev.reformator.stacktracedecoroutinator"
    version = "2.2.0-SNAPSHOT"
}
