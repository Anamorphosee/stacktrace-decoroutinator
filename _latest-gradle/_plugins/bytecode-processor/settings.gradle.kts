rootProject.name = "bytecode-processor"
include(
    "api",
    "plugins",
    "intrinsics",
    "gradle-plugin"
)
project(":api").name = "bytecode-processor-api"
project(":plugins").name = "bytecode-processor-plugins"
project(":intrinsics").name = "bytecode-processor-intrinsics"
project(":gradle-plugin").name = "bytecode-processor-gradle-plugin"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../../gradle/libs.versions.toml"))
        }
    }
}
