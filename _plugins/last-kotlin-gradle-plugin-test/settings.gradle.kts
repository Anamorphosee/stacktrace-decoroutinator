rootProject.name = "last-kotlin-gradle-plugin-test"

includeBuild("../decoroutinatortest")

pluginManagement {
    includeBuild("../decoroutinatortest")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}
