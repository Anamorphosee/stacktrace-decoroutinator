package dev.reformator.stacktracedecoroutinator.generator;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings({"KotlinInternalInJava", "unused"})
class JavaUtilsImpl implements JavaUtils {
    @NotNull
    @Override
    public Class<?> getMetadataAnnotationClass() {
        return kotlin.coroutines.jvm.internal.DebugMetadata.class;
    }

    @Nullable
    @Override
    public DebugMetadataInfo getDebugMetadataInfo(@NotNull Class<?> clazz) {
        kotlin.coroutines.jvm.internal.DebugMetadata metadata = clazz.getAnnotation(
                kotlin.coroutines.jvm.internal.DebugMetadata.class
        );
        if (metadata == null) {
            return null;
        }
        String internalClassName = metadata.c().replace('.', '/');
        Set<Integer> lineNumbers = Arrays.stream(metadata.l()).boxed().collect(Collectors.toSet());
        String fileName = metadata.f();
        if (fileName.isEmpty()) {
            fileName = null;
        }
        return new DebugMetadataInfo(internalClassName, metadata.m(), fileName, lineNumbers);
    }
}
