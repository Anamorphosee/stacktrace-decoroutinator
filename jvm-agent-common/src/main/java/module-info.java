import dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal.AgentBaseContinuationAccessorProvider;
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessorProvider;

module dev.reformator.stacktracedecoroutinator.jvmagentcommon {
    requires static intrinsics;
    requires static dev.reformator.bytecodeprocessor.intrinsics;

    requires kotlin.stdlib;
    requires dev.reformator.stacktracedecoroutinator.runtimesettings;
    requires dev.reformator.stacktracedecoroutinator.common;
    requires dev.reformator.stacktracedecoroutinator.generator;
    requires dev.reformator.stacktracedecoroutinator.provider;
    requires java.instrument;
    requires org.objectweb.asm;

    exports dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal to
            dev.reformator.stacktracedecoroutinator.jvm;

    provides BaseContinuationAccessorProvider with AgentBaseContinuationAccessorProvider;
}
