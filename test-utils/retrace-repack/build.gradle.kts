plugins {
    id("java")
    alias(libs.plugins.shadow)
    id("dev.reformator.forcevariantjavaversion")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.retrace)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_9
    targetCompatibility = JavaVersion.VERSION_1_9
}

tasks.shadowJar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    failOnDuplicateEntries = true
    archiveClassifier = ""
    relocate("proguard", "dev.reformator.retracerepack")
    excludes.remove("module-info.class")
    exclude("**/_dummy.class")
    exclude("com/**")
    exclude("kotlin/**")
    exclude("org/**")
    exclude("META-INF/versions/**")
    exclude("META-INF/*.kotlin_module")
    exclude("META-INF/services/*")
}
