import dev.reformator.stacktracedecoroutinator.common.internal.CommonSettingsProvider;
import dev.reformator.stacktracedecoroutinator.common.internal.AnnotationMetadataResolver;
import dev.reformator.stacktracedecoroutinator.provider.internal.DecoroutinatorProvider;
import dev.reformator.stacktracedecoroutinator.common.internal.SpecMethodsRegistry;
import dev.reformator.stacktracedecoroutinator.common.internal.Provider;

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
            dev.reformator.stacktracedecoroutinator.jvm.tests;

    provides DecoroutinatorProvider with Provider;

    uses SpecMethodsRegistry;
    uses CommonSettingsProvider;
    uses AnnotationMetadataResolver;
}
