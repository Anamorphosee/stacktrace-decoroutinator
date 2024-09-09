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
    "test-utils-jvm",
    "test-utils:custom-loader",
    "generator:jdk8-tests-g",
    "gradle-plugin:tests-gp",
    "gradle-plugin:android-tests",
    "gradle-plugin:jdk8-tests-gp",
    "jvm-agent:tests-ja",
    "jvm-agent:jdk8-tests-ja",
    "jvm:jdk8-tests-j"
)
project(":provider").name = "stacktrace-decoroutinator-provider"
project(":common").name = "stacktrace-decoroutinator-common"
project(":generator").name = "stacktrace-decoroutinator-generator"
project(":gradle-plugin").name = "stacktrace-decoroutinator-gradle-plugin"
project(":jvm-agent-common").name = "stacktrace-decoroutinator-jvm-agent-common"
project(":jvm").name = "stacktrace-decoroutinator-jvm"
project(":jvm-agent").name = "stacktrace-decoroutinator-jvm-agent"
project(":generator-android").name = "stacktrace-decoroutinator-generator-android"

includeBuild("_plugins/bytecode-processor")
includeBuild("_plugins/decoroutinatortest")
includeBuild("_plugins/force-variant-java-version")

pluginManagement {
    includeBuild("_plugins/bytecode-processor")
    includeBuild("_plugins/decoroutinatortest")
    includeBuild("_plugins/force-variant-java-version")
}
