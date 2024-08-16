import dev.reformator.stacktracedecoroutinator.provider.internal.DecoroutinatorProvider;
import dev.reformator.stacktracedecoroutinator.runtime.internal.StacktraceElementsFactory;
import dev.reformator.stacktracedecoroutinator.runtime.internal.Provider;

module dev.reformator.stacktracedecoroutinator.runtime {
    requires intrinsics;

    requires kotlin.stdlib;
    requires dev.reformator.stacktracedecoroutinator.provider;

    exports dev.reformator.stacktracedecoroutinator.runtime;
    exports dev.reformator.stacktracedecoroutinator.runtime.internal to dev.reformator.stacktracedecoroutinator.generator;

    provides DecoroutinatorProvider with Provider;

    uses StacktraceElementsFactory;
}
