rootProject.name = "decoroutinatortest"
include("embedded-debug-probes")

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
