import dev.reformator.stacktracedecoroutinator.common.internal.*;
import dev.reformator.stacktracedecoroutinator.provider.internal.DecoroutinatorProvider;

module dev.reformator.stacktracedecoroutinator.common {
    requires static dev.reformator.bytecodeprocessor.intrinsics;
    requires static intrinsics;

    requires kotlin.stdlib;
    requires dev.reformator.stacktracedecoroutinator.provider;

    exports dev.reformator.stacktracedecoroutinator.common;
    exports dev.reformator.stacktracedecoroutinator.common.internal to
            dev.reformator.stacktracedecoroutinator.generator,
            dev.reformator.stacktracedecoroutinator.jvmagentcommon,
            dev.reformator.stacktracedecoroutinator.jvm,
            dev.reformator.stacktracedecoroutinator.generator.tests,
            dev.reformator.stacktracedecoroutinator.jvm.tests,
            dev.reformator.stacktracedecoroutinator.mhinvoker,
            dev.reformator.stacktracedecoroutinator.mhinvokerjvm;

    provides DecoroutinatorProvider with Provider;

    uses SpecMethodsRegistry;
    uses CommonSettingsProvider;
    uses AnnotationMetadataResolver;
    uses MethodHandleInvoker;
    uses VarHandleInvoker;
}
