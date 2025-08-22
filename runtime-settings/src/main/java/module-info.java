import dev.reformator.stacktracedecoroutinator.runtimesettings.DecoroutinatorRuntimeSettingsProvider;

module dev.reformator.stacktracedecoroutinator.runtimesettings {
    requires kotlin.stdlib;

    exports dev.reformator.stacktracedecoroutinator.runtimesettings;
    exports dev.reformator.stacktracedecoroutinator.runtimesettings.internal to
            dev.reformator.stacktracedecoroutinator.common,
            dev.reformator.stacktracedecoroutinator.jvmagentcommon,
            kotlinx.coroutines.core;

    uses DecoroutinatorRuntimeSettingsProvider;
}
