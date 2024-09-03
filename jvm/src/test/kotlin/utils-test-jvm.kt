package dev.reformator.stacktracedecoroutinator.jvmtests

import dev.reformator.stacktracedecoroutinator.jvm.DecoroutinatorJvmApi

fun setupTest() {
    System.setProperty(
        "dev.reformator.stacktracedecoroutinator.jvmAgentDebugMetadataInfoResolveStrategy",
        "SYSTEM_RESOURCE"
    )
    DecoroutinatorJvmApi.install()
}
