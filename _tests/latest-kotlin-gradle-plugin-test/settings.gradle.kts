rootProject.name = "latest-kotlin-gradle-plugin-test"

includeBuild("../../_plugins/gradle-plugin-test")
includeBuild("../../_plugins/bytecode-processor")

pluginManagement {
    includeBuild("../../_plugins/gradle-plugin-test")
    includeBuild("../../_plugins/bytecode-processor")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}

include("custom-loader")
