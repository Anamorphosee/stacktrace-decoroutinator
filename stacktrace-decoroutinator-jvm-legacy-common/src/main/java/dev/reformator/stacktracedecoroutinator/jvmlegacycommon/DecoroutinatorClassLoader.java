package dev.reformator.stacktracedecoroutinator.jvmlegacycommon;

import org.jetbrains.annotations.NotNull;

public class DecoroutinatorClassLoader extends ClassLoader {
    static {
        registerAsParallelCapable();
    }

    public DecoroutinatorClassLoader() {
        super(null);
    }

    @NotNull
    public Class<?> defineClass(
            @NotNull String className,
            @NotNull byte[] classBody
    ) {
        synchronized (getClassLoadingLock(className)) {
            return defineClass(className, classBody, 0, classBody.length);
        }
    }
}
