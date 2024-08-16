import dev.reformator.stacktracedecoroutinator.provider.internal.DecoroutinatorProvider;

module dev.reformator.stacktracedecoroutinator.provider {
    requires org.jetbrains.annotations;

    exports dev.reformator.stacktracedecoroutinator.provider;
    exports dev.reformator.stacktracedecoroutinator.provider.stdlib to kotlin.stdlib;
    exports dev.reformator.stacktracedecoroutinator.provider.internal to dev.reformator.stacktracedecoroutinator.runtime;

    uses DecoroutinatorProvider;
}
