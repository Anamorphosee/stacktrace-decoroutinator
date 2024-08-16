rootProject.name = "stacktrace-decoroutinator"
include(
    "provider",
    "runtime",
    "generator",
    "gradle-plugin",
    "jvm-agent-common",
    "jvm",
    "jvm-agent",
    "generator-android",

    "intrinsics",
    "basecontinuation",
    "test-utils",
    "gradle-plugin-tests",
    "gradle-plugin-android-tests"
)
project(":provider").name = "stacktrace-decoroutinator-provider"
project(":runtime").name = "stacktrace-decoroutinator-runtime"
project(":generator").name = "stacktrace-decoroutinator-generator"
project(":gradle-plugin").name = "stacktrace-decoroutinator-gradle-plugin"
project(":jvm-agent-common").name = "stacktrace-decoroutinator-jvm-agent-common"
project(":jvm").name = "stacktrace-decoroutinator-jvm"
project(":jvm-agent").name = "stacktrace-decoroutinator-jvm-agent"
project(":generator-android").name = "stacktrace-decoroutinator-generator-android"

