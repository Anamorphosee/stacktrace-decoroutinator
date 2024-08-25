import dev.reformator.stacktracedecoroutinator.provider.internal.DecoroutinatorProvider;

module dev.reformator.stacktracedecoroutinator.provider {
    requires static kotlin.stdlib;
    requires static dev.reformator.bytecodeprocessor.intrinsics;

    exports dev.reformator.stacktracedecoroutinator.provider;
    exports dev.reformator.stacktracedecoroutinator.provider.internal to dev.reformator.stacktracedecoroutinator.common;

    uses DecoroutinatorProvider;
}
