package dev.reformator.stacktracedecoroutinator.generator.internal;

import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec;
import dev.reformator.stacktracedecoroutinator.runtime.internal.Utils_runtimeKt;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.intrinsics.IntrinsicsKt;
import kotlin.coroutines.jvm.internal.BaseContinuationImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
final class DecoroutinatorClassLoader extends ClassLoader {
    static {
        registerAsParallelCapable();
    }

    public DecoroutinatorClassLoader() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        super();
        Class<?> specClass = defineClass(DecoroutinatorSpec.class);
        Class<?> specImplClass = defineClass(GeneratorSpecImpl.class);
        specMethodType = MethodType.methodType(Object.class, specClass, Object.class);
        specImplConstructor = specImplClass.getConstructor(
                int.class,
                MethodHandle.class,
                Object.class,
                Object.class,
                Function.class
        );
        lookup = (MethodHandles.Lookup) specImplClass.getDeclaredMethod("getLookup").invoke(null);
    }

    @NotNull
    public Class<?> generateClass(
            @NotNull String className,
            @Nullable String fileName,
            @NotNull Map<String, Set<Integer>> methodName2LineNumbers
    ) {
        return defineClass(className, getClassBody(className, fileName, methodName2LineNumbers));
    }

    @NotNull
    public Object createSpecNotCallingNextHandle(int lineNumber, @NotNull Continuation<?> nextContinuation)
            throws InvocationTargetException, InstantiationException, IllegalAccessException {
        return createSpec(lineNumber, null, null, nextContinuation);
    }

    @NotNull
    public Object createSpecCallingNextHandle(
            int lineNumber,
            @NotNull MethodHandle nextHandle,
            @NotNull Object nextSpec,
            @NotNull Continuation<?> nextContinuation
    ) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        return createSpec(lineNumber, nextHandle, nextSpec, nextContinuation);
    }

    @NotNull
    public MethodHandle getSpecMethodHandle(@NotNull Class<?> clazz, @NotNull String methodName)
            throws NoSuchMethodException, IllegalAccessException {
        return lookup.findStatic(clazz, methodName, specMethodType);
    }

    private final MethodType specMethodType;
    private final Constructor<?> specImplConstructor;
    private final MethodHandles.Lookup lookup;

    @NotNull
    private Class<?> defineClass(@NotNull Class<?> originalClass) {
        String className = originalClass.getName();
        byte[] classBody = Utils_generatorKt.loadResource(className.replace('.', '/') + ".class");
        Objects.requireNonNull(classBody);
        return defineClass(originalClass.getName(), classBody);
    }

    @NotNull
    private Object createSpec(
            int lineNumber,
            @Nullable MethodHandle nextHandle,
            @Nullable Object nextSpec,
            @NotNull BaseContinuationImpl nextContinuation
    ) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        return specImplConstructor.newInstance(
                lineNumber,
                nextHandle,
                nextSpec,
                IntrinsicsKt.getCOROUTINE_SUSPENDED(),
                (Function<?, ?>) (result) -> Utils_runtimeKt.callInvokeSuspend(nextContinuation, result)
        );
    }

    @NotNull
    private Class<?> defineClass(@NotNull String className, @NotNull byte[] classBody) {
        synchronized (getClassLoadingLock(className)) {
            return defineClass(className, classBody, 0, classBody.length);
        }
    }

    @NotNull
    private static byte[] getClassBody(
            @NotNull String className,
            @Nullable String fileName,
            @NotNull Map<String, Set<Integer>> methodName2LineNumbers
    ) {
        ClassNode classNode = new ClassNode(Opcodes.ASM9);
        classNode.version = Opcodes.V1_8;
        classNode.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER;
        classNode.name = className.replace('.', '/');
        classNode.superName = Type.getInternalName(Object.class);
        classNode.sourceFile = fileName;
        classNode.methods = methodName2LineNumbers
                .entrySet()
                .stream()
                .map((entry) ->
                    StacktraceMethodBuilderKt.buildSpecMethodNode(entry.getKey(), entry.getValue(), false)
                )
                .collect(Collectors.toList());
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return writer.toByteArray();
    }
}
