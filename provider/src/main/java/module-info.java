import dev.reformator.stacktracedecoroutinator.provider.internal.DecoroutinatorProvider;

module dev.reformator.stacktracedecoroutinator.provider {
    requires static kotlin.stdlib;
    requires static dev.reformator.bytecodeprocessor.intrinsics;

    exports dev.reformator.stacktracedecoroutinator.provider;
    exports dev.reformator.stacktracedecoroutinator.provider.internal to
            kotlin.stdlib,
            dev.reformator.stacktracedecoroutinator.common,
            dev.reformator.stacktracedecoroutinator.mhinvoker,
            dev.reformator.stacktracedecoroutinator.generator,
            dev.reformator.stacktracedecoroutinator.test.basecontinuationaccessorstub,
            dev.reformator.stacktracedecoroutinator.jvmagentcommon;

    uses DecoroutinatorProvider;
}
