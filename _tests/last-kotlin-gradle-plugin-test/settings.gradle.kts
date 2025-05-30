rootProject.name = "last-kotlin-gradle-plugin-test"

includeBuild("../../_plugins/decoroutinatortest")
includeBuild("../../_plugins/bytecode-processor")

pluginManagement {
    includeBuild("../../_plugins/decoroutinatortest")
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
