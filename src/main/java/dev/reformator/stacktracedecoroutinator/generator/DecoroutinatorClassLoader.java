package dev.reformator.stacktracedecoroutinator.generator;

import dev.reformator.stacktracedecoroutinator.analyzer.DecoroutinatorClassSpec;
import dev.reformator.stacktracedecoroutinator.analyzer.DecoroutinatorMethodSpec;
import kotlin.jvm.functions.Function2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public class DecoroutinatorClassLoader extends ClassLoader {
    private final Function2<String, DecoroutinatorClassSpec, byte[]> generator;

    private static final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
    private static final MethodType invokeStacktraceMethodType = MethodType.methodType(
            Object.class,
            MethodHandle[].class,
            int[].class,
            int.class,
            BiFunction.class,
            Object.class,
            Object.class
    );

    static {
        registerAsParallelCapable();
    }

    public DecoroutinatorClassLoader(
            @Nullable ClassLoader parent,
            @NotNull Function2<String, DecoroutinatorClassSpec, byte[]> generator
    ) {
        super(parent);
        this.generator = generator;
    }

    public DecoroutinatorClassLoader(
            @NotNull Function2<String, DecoroutinatorClassSpec, byte[]> generator
    ) {
        this(null, generator);
    }

    public DecoroutinatorClassLoader(@Nullable ClassLoader parent) {
        this(parent, new DecoroutinatorClassBodyGeneratorImpl());
    }

    public DecoroutinatorClassLoader() {
        this((ClassLoader) null);
    }

    public @NotNull Map<String, MethodHandle> getMethodName2StacktraceHandlerMap(
            @NotNull String className,
            @NotNull DecoroutinatorClassSpec classSpec
    ) throws NoSuchMethodException, IllegalAccessException {
        Class<?> clazz = getClass(className, classSpec);
        Map<String, MethodHandle> result = new HashMap<>();
        for (DecoroutinatorMethodSpec method: classSpec.getContinuationClassName2Method().values()) {
            String methodName = method.getMethodName();
            if (!result.containsKey(methodName)) {
                result.put(methodName, lookup.findStatic(clazz, methodName, invokeStacktraceMethodType));
            }
        }
        return result;
    }

    private Class<?> getClass(String className, DecoroutinatorClassSpec classSpec) {
        synchronized (getClassLoadingLock(className)) {
            Class<?> clazz = findLoadedClass(className);
            if (clazz != null) {
                return clazz;
            }
            byte[] classBody = generator.invoke(className, classSpec);
            return defineClass(className, classBody, 0, classBody.length);
        }
    }
}
