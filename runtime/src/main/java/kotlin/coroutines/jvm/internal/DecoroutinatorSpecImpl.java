package kotlin.coroutines.jvm.internal;

import dev.reformator.stacktracedecoroutinator.runtime.DecoroutinatorSpec;
import kotlin.ResultKt;
import kotlin.coroutines.intrinsics.IntrinsicsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.util.Objects;

public final class DecoroutinatorSpecImpl implements DecoroutinatorSpec {
    private final int lineNumber;
    private final MethodHandle nextHandle;
    private final Object nextSpec;
    private final BaseContinuationImpl nextContinuation;

    public DecoroutinatorSpecImpl(
            int lineNumber,
            @NotNull MethodHandle nextHandle,
            @NotNull Object nextSpec,
            @NotNull BaseContinuationImpl nextContinuation
    ) {
        this.lineNumber = lineNumber;
        this.nextHandle = nextHandle;
        this.nextSpec = nextSpec;
        this.nextContinuation = nextContinuation;
    }

    public DecoroutinatorSpecImpl(int lineNumber,BaseContinuationImpl nextContinuation) {
        this.lineNumber = lineNumber;
        this.nextContinuation = nextContinuation;
        nextSpec = nextHandle = null;
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
        return IntrinsicsKt.getCOROUTINE_SUSPENDED();
    }

    @Nullable
    @Override
    @SuppressWarnings({"KotlinInternalInJava", "DataFlowIssue"})
    public Object resumeNext(@Nullable Object result) {
        DebugProbesKt.probeCoroutineResumed(nextContinuation);
        Object newResult;
        try {
            newResult = nextContinuation.invokeSuspend(result);
            if (newResult == IntrinsicsKt.getCOROUTINE_SUSPENDED()) {
                return newResult;
            }
        } catch (Throwable e) {
            newResult = ResultKt.createFailure(e);
        }
        nextContinuation.releaseIntercepted();
        return newResult;
    }
}
