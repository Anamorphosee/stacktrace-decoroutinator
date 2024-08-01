package dev.reformator.stacktracedecoroutinator.generator;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
class DecoroutinatorClassLoader extends ClassLoader {
    static {
        registerAsParallelCapable();
    }

    public DecoroutinatorClassLoader() {
        super();
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
