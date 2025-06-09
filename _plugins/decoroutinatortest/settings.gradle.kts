rootProject.name = "decoroutinatortest"
include("embedded")

includeBuild("../bytecode-processor")

pluginManagement {
    includeBuild("../bytecode-processor")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}
