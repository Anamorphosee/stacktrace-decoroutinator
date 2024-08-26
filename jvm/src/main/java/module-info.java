import dev.reformator.stacktracedecoroutinator.jvm.internal.CommonSettingsProviderImpl;
import dev.reformator.stacktracedecoroutinator.jvm.internal.JvmAgentCommonSettingsProviderImpl;
import dev.reformator.stacktracedecoroutinator.common.internal.CommonSettingsProvider;
import dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal.JvmAgentCommonSettingsProvider;

module dev.reformator.stacktracedecoroutinator.jvm {
    requires dev.reformator.stacktracedecoroutinator.provider;
    requires dev.reformator.stacktracedecoroutinator.jvmagentcommon;
    requires dev.reformator.stacktracedecoroutinator.common;
    requires net.bytebuddy.agent;
    requires java.instrument;
    requires kotlin.stdlib;

    exports dev.reformator.stacktracedecoroutinator.jvm;
    exports dev.reformator.stacktracedecoroutinator.runtime;

    provides CommonSettingsProvider with CommonSettingsProviderImpl;
    provides JvmAgentCommonSettingsProvider with JvmAgentCommonSettingsProviderImpl;
}
