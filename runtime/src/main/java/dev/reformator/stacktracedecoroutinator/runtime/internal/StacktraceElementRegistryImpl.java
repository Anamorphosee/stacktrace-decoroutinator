package dev.reformator.stacktracedecoroutinator.runtime.internal;

import kotlin.Pair;
import kotlin.coroutines.Continuation;
import kotlin.jvm.internal.Ref;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings({"KotlinInternalInJava", "unused"})
class StacktraceElementRegistryImpl implements StacktraceElementRegistry {

    private final ConcurrentHashMap<Class<?>, ContinuationClassSpec> continuationClass2Spec =
            new ConcurrentHashMap<>();

    private volatile Map<Class<?>, ContinuationClassSpec> notSynchronizedContinuationClass2Spec =
            Collections.emptyMap();

    private final Function<Class<?>, ContinuationClassSpec> continuationClass2SpecFun = continuationClass -> {
        kotlin.coroutines.jvm.internal.DebugMetadata metadata = continuationClass.getAnnotation(
                kotlin.coroutines.jvm.internal.DebugMetadata.class
        );
        if (metadata == null) {
            return null;
        }
        try {
            return new ContinuationClassSpec(continuationClass, metadata);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    };

    @NotNull
    @Override
    public StacktraceElements getStacktraceElements(
            @NotNull Collection<? extends Continuation<?>> continuations
    ) {
        Map<Class<?>, ContinuationClassSpec> cachedClass2Spec = notSynchronizedContinuationClass2Spec;
        Ref.BooleanRef needUpdateClass2Spec = new Ref.BooleanRef();
        Map<Class<?>, ContinuationClassSpec> continuationClass2Spec = continuations.stream()
                .map(Object::getClass)
                .distinct()
                .map(continuationClass -> {
                    ContinuationClassSpec spec = cachedClass2Spec.get(continuationClass);
                    if (spec == null) {
                        spec = this.continuationClass2Spec.computeIfAbsent(
                                continuationClass,
                                continuationClass2SpecFun
                        );
                        needUpdateClass2Spec.element = true;
                    }
                    return spec;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(ContinuationClassSpec::getContinuationClass, Function.identity()));

        if (needUpdateClass2Spec.element) {
            updateContinuationClass2Spec();
        }

        Map<Continuation<?>, StacktraceElement> continuation2Element = continuations.stream()
                .map(continuation -> {
                    ContinuationClassSpec spec = continuationClass2Spec.get(continuation.getClass());
                    if (spec == null) {
                        return null;
                    }
                    StacktraceElement element;
                    try {
                        element = spec.getElement(continuation);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                    return new Pair<>(continuation, element);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));

        Set<StacktraceElement> possibleElements = continuationClass2Spec.values().stream()
                .flatMap(spec -> Arrays.stream(spec.getLabelIndex2element()))
                .collect(Collectors.toSet());

        return new StacktraceElements(
                continuation2Element,
                possibleElements
        );
    }

    private void updateContinuationClass2Spec() {
        while (true) {
            try {
                notSynchronizedContinuationClass2Spec = new HashMap<>(continuationClass2Spec);
                return;
            } catch (ConcurrentModificationException ignored) { }
        }
    }
}

@SuppressWarnings("KotlinInternalInJava")
class ContinuationClassSpec {
    private final Class<?> continuationClass;
    private final Field field;
    private final StacktraceElement[] labelIndex2element;
    private final StacktraceElement noLineNumberElement;

    ContinuationClassSpec(
            Class<?> continuationClass,
            kotlin.coroutines.jvm.internal.DebugMetadata debugMetadata
    ) throws NoSuchFieldException {
        this.continuationClass = continuationClass;
        labelIndex2element = new StacktraceElement[debugMetadata.l().length];
        for (int i = 0; i < debugMetadata.l().length; i++) {
            int lineNumber = debugMetadata.l()[i];
            labelIndex2element[i] = new StacktraceElement(
                    debugMetadata.c(),
                    debugMetadata.f(),
                    debugMetadata.m(),
                    lineNumber
            );
        }
        field = continuationClass.getDeclaredField("label");
        field.setAccessible(true);
        noLineNumberElement = new StacktraceElement(
                debugMetadata.c(),
                debugMetadata.f(),
                debugMetadata.m(),
                -1
        );
    }

    Class<?> getContinuationClass() {
        return continuationClass;
    }

    StacktraceElement[] getLabelIndex2element() {
        return labelIndex2element;
    }

    StacktraceElement getElement(Continuation<?> continuation) throws IllegalAccessException {
        int labelIndex = field.getInt(continuation) - 1;
        if (labelIndex < 0) {
            return noLineNumberElement;
        } else {
            return labelIndex2element[labelIndex];
        }
    }
}
