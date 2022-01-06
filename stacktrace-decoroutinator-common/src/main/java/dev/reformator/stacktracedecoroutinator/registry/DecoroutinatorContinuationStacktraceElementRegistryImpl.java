package dev.reformator.stacktracedecoroutinator.registry;

import dev.reformator.stacktracedecoroutinator.DecoroutinatorStacktraceElement;
import kotlin.Pair;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.jvm.internal.DebugMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DecoroutinatorContinuationStacktraceElementRegistryImpl
        implements DecoroutinatorContinuationStacktraceElementRegistry {

    private final ConcurrentHashMap<Class<?>, ContinuationClassSpec> continuationClass2Spec = new ConcurrentHashMap<>();
    private final Function<Class<?>, ContinuationClassSpec> continuationClass2SpecFun = continuationClass -> {
        DebugMetadata metadata = continuationClass.getAnnotation(DebugMetadata.class);
        if (metadata == null) {
            return null;
        }
        try {
            return new ContinuationClassSpec(continuationClass, metadata);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    };
    private final Function<Class<?>, ContinuationClassSpec> continuationClass2SpecCachedFun = continuationClass ->
            continuationClass2Spec.computeIfAbsent(continuationClass, continuationClass2SpecFun);

    @NotNull
    @Override
    public DecoroutinatorContinuationStacktraceElements getStacktraceElements(
            @NotNull Collection<? extends Continuation<?>> continuations
    ) {
        Map<Class<?>, ContinuationClassSpec> continuationClass2Spec = continuations.stream()
                .map(Object::getClass)
                .distinct()
                .map(continuationClass2SpecCachedFun)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(ContinuationClassSpec::getContinuationClass, Function.identity()));

        Map<Continuation<?>, DecoroutinatorStacktraceElement> continuation2Element = continuations.stream()
                .map(continuation -> {
                    ContinuationClassSpec spec = continuationClass2Spec.get(continuation.getClass());
                    if (spec == null) {
                        return null;
                    }
                    DecoroutinatorStacktraceElement element = null;
                    try {
                        element = spec.getElement(continuation);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                    return new Pair<>(continuation, element);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));

        Set<DecoroutinatorStacktraceElement> possibleElements = continuationClass2Spec.values().stream()
                .flatMap(spec -> Arrays.stream(spec.getLabelIndex2element()))
                .collect(Collectors.toSet());

        return new DecoroutinatorContinuationStacktraceElements(
                continuation2Element,
                possibleElements
        );
    }
}

class ContinuationClassSpec {
    private final Class<?> continuationClass;
    private final Field field;
    private final DecoroutinatorStacktraceElement[] labelIndex2element;
    private final DecoroutinatorStacktraceElement noLineNumberElement;

    ContinuationClassSpec(Class<?> continuationClass, DebugMetadata debugMetadata) throws NoSuchFieldException {
        this.continuationClass = continuationClass;
        labelIndex2element = new DecoroutinatorStacktraceElement[debugMetadata.l().length];
        for (int i = 0; i < debugMetadata.l().length; i++) {
            int lineNumber = debugMetadata.l()[i];
            labelIndex2element[i] = new DecoroutinatorStacktraceElement(
                    debugMetadata.c(),
                    debugMetadata.f(),
                    debugMetadata.m(),
                    lineNumber
            );
        }
        field = continuationClass.getDeclaredField("label");
        field.setAccessible(true);
        noLineNumberElement = new DecoroutinatorStacktraceElement(
                debugMetadata.c(),
                debugMetadata.f(),
                debugMetadata.m(),
                -1
        );
    }

    Class<?> getContinuationClass() {
        return continuationClass;
    }

    DecoroutinatorStacktraceElement[] getLabelIndex2element() {
        return labelIndex2element;
    }

    DecoroutinatorStacktraceElement getElement(Continuation<?> continuation) throws IllegalAccessException {
        int labelIndex = field.getInt(continuation) - 1;
        if (labelIndex < 0) {
            return noLineNumberElement;
        } else {
            return labelIndex2element[labelIndex];
        }
    }
}
