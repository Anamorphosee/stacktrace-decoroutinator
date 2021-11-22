package dev.reformator.stacktracedecoroutinator.util;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.jvm.internal.BaseContinuationImpl;
import kotlin.coroutines.jvm.internal.DebugMetadataKt;
import kotlin.coroutines.jvm.internal.DebugProbesKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaUtilImpl extends JavaUtil {
    private JavaUtilImpl() { }

    @NotNull
    public static final JavaUtil instance = new JavaUtilImpl();

    @Nullable
    @Override
    public Object retrieveResultValue(@Nullable Object result) {
        return result;
    }

    public static void probeCoroutineResumed(@NotNull Continuation<?> frame) {
        DebugProbesKt.probeCoroutineResumed(frame);
    }

    @Nullable
    public static StackTraceElement getStackTraceElementImpl(@NotNull BaseContinuationImpl continuation) {
        return DebugMetadataKt.getStackTraceElement(continuation);
    }
}
