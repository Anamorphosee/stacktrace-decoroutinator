package kotlin.coroutines.jvm.internal;

import dev.reformator.stacktracedecoroutinator.runtime.JavaUtils;
import kotlin.Result;
import kotlin.ResultKt;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.intrinsics.IntrinsicsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiFunction;

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

    @SuppressWarnings({"unchecked", "Convert2Lambda", "rawtypes"})
    @NotNull
    @Override
    public BiFunction<Integer, Object, Object> createAwakenerFun(
            @NotNull List<? extends BaseContinuationImpl> baseContinuations
    ) {
        return new BiFunction() {
            @Override
            public Object apply(Object index, Object innerResult) {
                BaseContinuationImpl continuation = baseContinuations.get((Integer) index );
                DebugProbesKt.probeCoroutineResumed(continuation);
                Object newResult;
                try {
                    newResult = continuation.invokeSuspend(innerResult);
                    if (newResult == IntrinsicsKt.getCOROUTINE_SUSPENDED()) {
                        return newResult;
                    }
                } catch (Throwable e) {
                    newResult = ResultKt.createFailure(e);
                }
                continuation.releaseIntercepted();
                return newResult;
            }
        };
    }
}
