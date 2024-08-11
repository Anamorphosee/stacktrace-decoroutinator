package dev.reformator.stacktracedecoroutinator.generator;

import dev.reformator.stacktracedecoroutinator.runtime.DecoroutinatorSpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.function.Function;

public final class GeneratorSpecImpl implements DecoroutinatorSpec {
    private final int lineNumber;
    private final MethodHandle nextHandle;
    private final Object nextSpec;
    private final Object coroutineSuspendedMarker;
    private final Function<Object, Object> resumeNext;

    public GeneratorSpecImpl(
            int lineNumber,
            @Nullable MethodHandle nextHandle,
            @Nullable Object nextSpec,
            @NotNull Object coroutineSuspendedMarker,
            @NotNull Function<Object, Object> resumeNext
    ) {
        this.lineNumber = lineNumber;
        this.nextHandle = nextHandle;
        this.nextSpec = nextSpec;
        this.coroutineSuspendedMarker = coroutineSuspendedMarker;
        this.resumeNext = resumeNext;
    }

    @Override
    public int getLineNumber() {
        return lineNumber;
    }

    @Override
    public boolean isLastSpec() {
        return nextHandle == null;
    }

    @NotNull
    @Override
    public MethodHandle getNextHandle() {
        Objects.requireNonNull(nextHandle);
        return nextHandle;
    }

    @NotNull
    @Override
    public Object getNextSpec() {
        Objects.requireNonNull(nextSpec);
        return nextSpec;
    }

    @NotNull
    @Override
    public Object getCoroutineSuspendedMarker() {
        return coroutineSuspendedMarker;
    }

    @Nullable
    @Override
    public Object resumeNext(@Nullable Object result) {
        return resumeNext.apply(result);
    }

    public static MethodHandles.Lookup getLookup() {
        return MethodHandles.lookup();
    }
}
