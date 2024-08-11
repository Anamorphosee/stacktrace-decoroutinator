package kotlin.coroutines.jvm.internal;

import dev.reformator.stacktracedecoroutinator.runtime.internal.JavaUtils;
import kotlin.Result;
import kotlin.ResultKt;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("KotlinInternalInJava")
public final class JavaUtilsImpl implements JavaUtils {
    @Nullable
    @Override
    public Object retrieveResultValue(@Nullable Object result) {
        return result;
    }

    @NotNull
    @Override
    public Throwable retrieveResultThrowable(@NotNull Object result) {
        return ((Result.Failure) result).exception;
    }

    @Override
    public void probeCoroutineResumed(@NotNull Continuation<?> frame) {
        DebugProbesKt.probeCoroutineResumed(frame);
    }

    @Nullable
    @Override
    public StackTraceElement getStackTraceElementImpl(@NotNull BaseContinuationImpl continuation) {
        return DebugMetadataKt.getStackTraceElement(continuation);
    }

    @NotNull
    @Override
    public Object createFailureResult(@NotNull Throwable exception) {
        return ResultKt.createFailure(exception);
    }

    @Nullable
    @Override
    public Object baseContinuationInvokeSuspend(
            @NotNull BaseContinuationImpl baseContinuation,
            @NotNull Object result
    ) {
        return baseContinuation.invokeSuspend(result);
    }

    @Override
    public void baseContinuationReleaseIntercepted(@NotNull BaseContinuationImpl baseContinuation) {
        baseContinuation.releaseIntercepted();
    }
}
