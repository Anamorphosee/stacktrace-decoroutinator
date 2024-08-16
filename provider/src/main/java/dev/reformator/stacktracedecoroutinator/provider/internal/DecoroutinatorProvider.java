package dev.reformator.stacktracedecoroutinator.provider.internal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.util.ServiceLoader;

public interface DecoroutinatorProvider {
    boolean isPrepared();
    boolean isEnabled();
    void prepare(@NotNull MethodHandles.Lookup lookup);
    void awake(@NotNull Object baseContinuation, @Nullable Object result);
    void registerTransformedClass(@NotNull MethodHandles.Lookup lookup);

    @NotNull DecoroutinatorProvider instance = ServiceLoader.load(DecoroutinatorProvider.class).iterator().next();
}
