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
    "mh-invoker",
    "mh-invoker-android",

    "intrinsics",
    "test-utils",
    "test-utils-jvm",
    "test-utils:custom-loader",
    "generator:jdk8-tests-g",
    "gradle-plugin:tests-gp",
    "gradle-plugin:android-tests",
    "gradle-plugin:jdk8-tests-gp",
    "gradle-plugin:duplicate-entity-jar-builder",
    "gradle-plugin:android-legacy-tests",
    "jvm-agent:tests-ja",
    "jvm-agent:jdk8-tests-ja",
    "jvm:jdk8-tests-j",
    "mh-invoker-android:legacy-tests"
)
project(":provider").name = "stacktrace-decoroutinator-provider"
project(":common").name = "stacktrace-decoroutinator-common"
project(":generator").name = "stacktrace-decoroutinator-generator"
project(":gradle-plugin").name = "stacktrace-decoroutinator-gradle-plugin"
project(":jvm-agent-common").name = "stacktrace-decoroutinator-jvm-agent-common"
project(":jvm").name = "stacktrace-decoroutinator-jvm"
project(":jvm-agent").name = "stacktrace-decoroutinator-jvm-agent"
project(":generator-android").name = "stacktrace-decoroutinator-generator-android"
project(":mh-invoker").name = "stacktrace-decoroutinator-mh-invoker"
project(":mh-invoker-android").name = "stacktrace-decoroutinator-mh-invoker-android"

includeBuild("_plugins/bytecode-processor")
includeBuild("_plugins/decoroutinatortest")
includeBuild("_plugins/force-variant-java-version")

includeBuild("_tests/last-kotlin-gradle-plugin-test")
includeBuild("_tests/last-kotlin-kmp-gradle-plugin-test")

pluginManagement {
    includeBuild("_plugins/bytecode-processor")
    includeBuild("_plugins/decoroutinatortest")
    includeBuild("_plugins/force-variant-java-version")
}
