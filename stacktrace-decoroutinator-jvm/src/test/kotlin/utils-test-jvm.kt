import dev.reformator.stacktracedecoroutinator.runtime.DecoroutinatorRuntime

fun setupTest() {
    System.setProperty(
        "dev.reformator.stacktracedecoroutinator.jvmAgentDebugMetadataInfoResolveStrategy",
        "SYSTEM_RESOURCE"
    )
    DecoroutinatorRuntime.load()
}
