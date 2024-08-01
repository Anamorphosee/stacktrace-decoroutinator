rootProject.name = "stacktrace-decoroutinator"
include(
    "runtime",
    "generator",
    "gradle-plugin",
    "jvm-agent-common",
    "jvm",
    "jvm-agent",

    "test-utils",
    "gradle-plugin-tests",
    "generator-runtime-tests",
    "gradle-plugin-android-tests"
)
project(":runtime").name = "stacktrace-decoroutinator-runtime"
project(":generator").name = "stacktrace-decoroutinator-generator"
project(":gradle-plugin").name = "dev.reformator.stacktracedecoroutinator.gradle.plugin"
project(":jvm-agent-common").name = "stacktrace-decoroutinator-jvm-agent-common"
project(":jvm").name = "stacktrace-decoroutinator-jvm"
project(":jvm-agent").name = "stacktrace-decoroutinator-jvm-agent"

