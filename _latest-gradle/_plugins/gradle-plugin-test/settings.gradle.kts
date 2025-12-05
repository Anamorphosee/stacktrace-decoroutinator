rootProject.name = "gradle-plugin-test"

include("embedded-debug-probes-stdlib")
include("embedded-debug-probes-xcoroutines")
include("base-continuation-accessor")
include("runtime-settings")
include("intrinsics")
include("provider")

includeBuild("../bytecode-processor")

pluginManagement {
    includeBuild("../bytecode-processor")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../../gradle/libs.versions.toml"))
        }
    }
}
