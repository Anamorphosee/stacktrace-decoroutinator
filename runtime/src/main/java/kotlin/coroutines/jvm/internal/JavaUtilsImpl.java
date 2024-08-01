package kotlin.coroutines.jvm.internal;

import dev.reformator.stacktracedecoroutinator.runtime.JavaUtils;
import kotlin.Result;
import kotlin.ResultKt;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("KotlinInternalInJava")
public class JavaUtilsImpl implements JavaUtils {
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

    @Nullable
    @Override
    public Object invokeSuspend(@NotNull BaseContinuationImpl continuation, @NotNull Object result) {
        return continuation.invokeSuspend(result);
    }

    @Override
    public void releaseIntercepted(@NotNull BaseContinuationImpl continuation) {
        continuation.releaseIntercepted();
    }

    @NotNull
    @Override
    public Object createFailure(@NotNull Throwable exception) {
        return ResultKt.createFailure(exception);
    }
}
