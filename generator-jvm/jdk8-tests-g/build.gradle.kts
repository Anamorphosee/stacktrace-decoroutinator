plugins {
    kotlin("jvm")
    id("decoroutinatorTransformBaseContinuation")
}

repositories {
    mavenCentral()
}

dependencies {
    testRuntimeOnly(project(":stacktrace-decoroutinator-generator-jvm"))
    testRuntimeOnly(project(":stacktrace-decoroutinator-mh-invoker"))

    testImplementation(kotlin("test"))
    testImplementation(project(":test-utils"))
    testImplementation(project(":test-utils-jvm"))
    testRuntimeOnly(project(":test-utils:base-continuation-accessor-stub"))
}

afterEvaluate {
    configurations.testRuntimeClasspath.get().attributes.attribute(decoroutinatorTransformedBaseContinuationAttribute, true)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}
