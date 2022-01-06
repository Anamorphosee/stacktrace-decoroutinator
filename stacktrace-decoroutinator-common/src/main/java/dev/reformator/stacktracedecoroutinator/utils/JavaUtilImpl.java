package dev.reformator.stacktracedecoroutinator.utils;

import kotlin.Result;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.jvm.internal.BaseContinuationImpl;
import kotlin.coroutines.jvm.internal.DebugMetadataKt;
import kotlin.coroutines.jvm.internal.DebugProbesKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaUtilImpl extends JavaUtil {
    private JavaUtilImpl() { }

    @NotNull
    public static final JavaUtil instance = new JavaUtilImpl();

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

    public static void probeCoroutineResumed(@NotNull Continuation<?> frame) {
        DebugProbesKt.probeCoroutineResumed(frame);
    }

    @Nullable
    public static StackTraceElement getStackTraceElementImpl(@NotNull BaseContinuationImpl continuation) {
        return DebugMetadataKt.getStackTraceElement(continuation);
    }
}
