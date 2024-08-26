import dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal.JvmAgentCommonSettingsProvider;

module dev.reformator.stacktracedecoroutinator.jvmagentcommon {
    requires kotlin.stdlib;
    requires dev.reformator.stacktracedecoroutinator.common;
    requires dev.reformator.stacktracedecoroutinator.generator;
    requires dev.reformator.stacktracedecoroutinator.provider;
    requires java.instrument;
    requires org.objectweb.asm;

    exports dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal to dev.reformator.stacktracedecoroutinator.jvmagent;

    uses JvmAgentCommonSettingsProvider;
}
