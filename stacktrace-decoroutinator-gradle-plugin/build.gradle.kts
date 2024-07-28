plugins {
    `java-gradle-plugin`
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("decoroutinatorPlugin") {
            id = "dev.reformator.stacktracedecoroutinator"
            implementationClass = "dev.reformator.stacktracedecoroutinator.gradleplugin.DecoroutinatorPlugin"
        }
    }
}

dependencies {
    implementation(project(":stacktrace-decoroutinator-runtime"))
    implementation(project(":stacktrace-decoroutinator-generator"))

    testImplementation(kotlin("test"))
}

tasks.named("processResources").get().doLast {
    val propertiesFile = layout.buildDirectory.get()
        .dir("resources")
        .dir("main")
        .file("dev.reformator.stacktracedecoroutinator.gradleplugin.properties")
    propertiesFile.asFile.outputStream().writer().use { output ->
        output.write("version=$version\n")
    }
}

kotlin {
    jvmToolchain(8)
}

tasks.test {
    useJUnitPlatform()
    dependsOn(":gradle-plugin-tests:test")
}
