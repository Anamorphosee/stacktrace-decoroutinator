plugins {
    kotlin("jvm")
    id("dev.reformator.stacktracedecoroutinator")
}

stacktraceDecoroutinator {
    _addRuntimeDependency = false
}

repositories {
    mavenCentral()
}

dependencies {
    testRuntimeOnly(project(":stacktrace-decoroutinator-runtime"))

    testImplementation(project(":test-utils"))
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${decoroutinatorVersions["kotlinxCoroutines"]}")
}

tasks.test {
    useJUnitPlatform()
}

fun printConfigs() {
    configurations.forEach { conf ->
        println("**CONFIGURATION: ${conf.name}")
        conf.outgoing.variants.forEach { variant ->
            println("****OUTGOING VARIANT: ${variant.name}")
            variant.artifacts.forEach { artifact ->
                println("******ARTIFACT: ${artifact.name} - ${artifact.file} - ${artifact.type}")
            }
            variant.attributes.keySet().forEach { attribute ->
                println("******ATTRIBUTE: ${attribute.name} - ${variant.attributes.getAttribute(attribute)}")
            }
        }
        conf.artifacts.forEach { artifact ->
            println("****ARTIFACT:  ${artifact.name} - ${artifact.file} - ${artifact.type}")
        }
    }
}

afterEvaluate {
    printConfigs()
}