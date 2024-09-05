rootProject.name = "bytecode-processor"
include(
    "plugin-api",
    "plugins",
    "intrinsics",
    "impl",
    "gradle-plugin"
)
project(":plugin-api").name = "bytecode-processor-plugin-api"
project(":plugins").name = "bytecode-processor-plugins"
project(":intrinsics").name = "bytecode-processor-intrinsics"
project(":impl").name = "bytecode-processor-impl"
project(":gradle-plugin").name = "bytecode-processor-gradle-plugin"

includeBuild("../force-variant-java-version")

pluginManagement {
    includeBuild("../force-variant-java-version")
}
