rootProject.name = "last-kotlin-gradle-plugin-test"

includeBuild("../decoroutinatortest")
includeBuild("../bytecode-processor")

pluginManagement {
    includeBuild("../decoroutinatortest")
    includeBuild("../bytecode-processor")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}
