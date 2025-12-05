include("gradle-plugin-android-test")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

includeBuild("_plugins/gradle-plugin-test")

pluginManagement {
    includeBuild("_plugins/gradle-plugin-test")
}
