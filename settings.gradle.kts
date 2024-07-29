rootProject.name = "stacktrace-decoroutinator"
include(
    "stacktrace-decoroutinator-runtime",
    "stacktrace-decoroutinator-generator",
    "gradle-plugin",

    "stacktrace-decoroutinator-jvm-agent-common",
    "stacktrace-decoroutinator-jvm",
    "stacktrace-decoroutinator-jvm-agent",

    "test-utils",
    "gradle-plugin-tests"
)
project(":gradle-plugin").name = "dev.reformator.stacktracedecoroutinator.gradle.plugin"
