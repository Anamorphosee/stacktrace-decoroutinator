package dev.reformator.stacktracedecoroutinator.registry;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.jvm.internal.DebugMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;

public class DecoroutinatorContinuationStacktraceElementRegistryImpl
        implements DecoroutinatorContinuationStacktraceElementRegistry {

    private final HashMap<Class<?>, ContinuationClassSpec> continuationClass2Spec = new HashMap<>();

    @Nullable
    @Override
    public ContinuationStacktraceElement getStacktraceElement(@NotNull Continuation<?> continuation) throws IllegalAccessException {
        Class<?> continuationClass = continuation.getClass();
        ContinuationClassSpec classSpec = continuationClass2Spec.computeIfAbsent(continuationClass, (continuationClassCopy) -> {
            DebugMetadata metadata = continuationClassCopy.getAnnotation(DebugMetadata.class);
            if (metadata == null) {
                return null;
            }
            try {
                return new ContinuationClassSpec(continuationClassCopy, metadata);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        });

        if (classSpec == null) {
            return null;
        }

        return classSpec.getStacktraceElement(continuation);
    }
}

class ContinuationClassSpec {
    private final ContinuationStacktraceElementSummary summary;
    private final Field field;
    private final ContinuationStacktraceElement[] labelIndex2stacktraceElement;

    ContinuationClassSpec(Class<?> continuationClass, DebugMetadata debugMetadata) throws NoSuchFieldException {
        summary = new ContinuationStacktraceElementSummary(
                debugMetadata.c(),
                debugMetadata.f(),
                debugMetadata.m(),
                Arrays.stream(debugMetadata.l()).boxed().collect(Collectors.toSet())
        );
        if (debugMetadata.l().length == 1) {
            field = null;
            labelIndex2stacktraceElement = new ContinuationStacktraceElement[] {
                    new ContinuationStacktraceElement(summary, summary.getPossibleLineNumbers().iterator().next())
            };
        } else {
            field = continuationClass.getDeclaredField("label");
            field.setAccessible(true);
            labelIndex2stacktraceElement = Arrays.stream(debugMetadata.l())
                    .mapToObj(lineNumber -> new ContinuationStacktraceElement(summary, lineNumber))
                    .toArray(ContinuationStacktraceElement[]::new);
        }
    }

    ContinuationStacktraceElement getStacktraceElement(Continuation<?> continuation) throws IllegalAccessException {
        if (labelIndex2stacktraceElement.length == 1) {
            return labelIndex2stacktraceElement[0];
        } else {
            int labelIndex = field.getInt(continuation) - 1;
            return labelIndex2stacktraceElement[labelIndex];
        }
    }
}
