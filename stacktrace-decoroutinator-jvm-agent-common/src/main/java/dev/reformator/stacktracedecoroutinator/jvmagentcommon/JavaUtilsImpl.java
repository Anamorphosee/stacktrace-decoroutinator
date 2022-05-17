package dev.reformator.stacktracedecoroutinator.jvmagentcommon;

import kotlin.coroutines.jvm.internal.DebugMetadata;
import org.jetbrains.annotations.NotNull;

public class JavaUtilsImpl {
    private JavaUtilsImpl() { }

    @NotNull
    public static final Class<?> metadataAnnotationClass = DebugMetadata.class;
}
