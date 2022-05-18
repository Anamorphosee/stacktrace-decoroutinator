package dev.reformator.stacktracedecoroutinator.jvmagentcommon;

import kotlin.coroutines.jvm.internal.DebugMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class JavaUtilsImpl implements JavaUtils {
    private JavaUtilsImpl() { }

    public static JavaUtilsImpl instance = new JavaUtilsImpl();

    @NotNull
    public static final Class<?> metadataAnnotationClass = DebugMetadata.class;

    @Nullable
    @Override
    public DebugMetadataInfo getDebugMetadataInfo(@NotNull String className) {
        Class<?> clazz = null;
        try {
            clazz = Class.forName(className, false, getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
        DebugMetadata metadata = clazz.getAnnotation(DebugMetadata.class);
        if (metadata == null) {
            return null;
        }
        String internalClassName = metadata.c().replace('.', '/');
        Set<Integer> lineNumbers = Arrays.stream(metadata.l()).boxed().collect(Collectors.toSet());
        return new DebugMetadataInfo(internalClassName, metadata.m(), lineNumbers);
    }
}
