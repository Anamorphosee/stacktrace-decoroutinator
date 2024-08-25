rootProject.name = "stacktrace-decoroutinator"
include(
    "provider",
    "common",
    "generator",
    "gradle-plugin",
    "jvm-agent-common",
    "jvm",
    "jvm-agent",
    "generator-android",

    "intrinsics",
    "test-utils",
    "gradle-plugin-tests",
    "gradle-plugin-android-tests"
)
project(":provider").name = "stacktrace-decoroutinator-provider"
project(":common").name = "stacktrace-decoroutinator-common"
project(":generator").name = "stacktrace-decoroutinator-generator"
project(":gradle-plugin").name = "stacktrace-decoroutinator-gradle-plugin"
project(":jvm-agent-common").name = "stacktrace-decoroutinator-jvm-agent-common"
project(":jvm").name = "stacktrace-decoroutinator-jvm"
project(":jvm-agent").name = "stacktrace-decoroutinator-jvm-agent"
project(":generator-android").name = "stacktrace-decoroutinator-generator-android"

includeBuild("_external/bytecode-processor")

pluginManagement {
    includeBuild("_external/bytecode-processor")
}