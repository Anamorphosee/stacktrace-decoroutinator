package dev.reformator.stacktracedecoroutinator.utils

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.function.BiFunction

internal abstract class JavaUtil {
    abstract fun retrieveResultValue(result: Result<*>): Any?
}

internal val lookup: MethodHandles.Lookup = MethodHandles.publicLookup()

internal val invokeStacktraceMethodType: MethodType = MethodType.methodType(
    Object::class.java,
    Array<MethodHandle>::class.java, //stacktrace handles
    IntArray::class.java, //line numbers
    Int::class.javaPrimitiveType, //next step index
    BiFunction::class.java, //invoke coroutine function
    Object::class.java, //coroutine result
    Object::class.java //COROUTINE_SUSPENDED
)

internal val unknownStacktraceMethodHandle: MethodHandle = lookup.findStatic(
    Class.forName("UnknownKt"),
    "unknown",
    invokeStacktraceMethodType
)

internal inline fun callStacktraceHandles(
    stacktraceHandles: Array<MethodHandle>,
    lineNumbers: IntArray,
    nextStepIndex: Int,
    invokeCoroutineFunction: BiFunction<Int, Any?, Any?>,
    result: Any?,
    coroutineSuspend: Any
): Any? {
    val updatedResult = if (nextStepIndex < stacktraceHandles.size) {
        val updatedResult = stacktraceHandles[nextStepIndex].invokeExact(
            stacktraceHandles,
            lineNumbers,
            nextStepIndex + 1,
            invokeCoroutineFunction,
            result,
            coroutineSuspend
        )
        if (updatedResult === coroutineSuspend) {
            return coroutineSuspend
        }
        updatedResult
    } else {
        result
    }
    return invokeCoroutineFunction.apply(nextStepIndex, updatedResult)
}

internal inline fun invokeAndWrapResultOrReturnOnCoroutineSuspend(
    coroutineSuspend: Any,
    returnBlock: () -> Nothing,
    invokeBlock: () -> Any?
): Result<*> {
    return try {
        val invokeResult = invokeBlock()
        if (invokeResult === coroutineSuspend) {
            returnBlock()
        } else {
            Result.success(invokeResult)
        }
    } catch (e: Throwable) {
        Result.failure<Any?>(e)
    }
}

inline val Any.classLoader: ClassLoader?
    get() = javaClass.classLoader

fun ClassLoader.getClassLoadingLock(className: String): Any {
    val method = ClassLoader::class.java.getDeclaredMethod("getClassLoadingLock", String::class.java)
    method.isAccessible = true
    return method.invoke(this, className)
}

fun ClassLoader.getClassIfLoaded(className: String): Class<*>? {
    val method = ClassLoader::class.java.getDeclaredMethod("findLoadedClass", String::class.java)
    method.isAccessible = true
    return synchronized(getClassLoadingLock(className)) {
        method.invoke(this, className) as Class<*>?
    }
}

fun ClassLoader.loadClass(className: String, classBody: ByteArray): Class<*> {
    val method = ClassLoader::class.java.getDeclaredMethod("defineClass", String::class.java,
        ByteArray::class.java, Integer.TYPE, Integer.TYPE)!!
    method.isAccessible = true
    return synchronized(getClassLoadingLock(className)) {
        method.invoke(this, className, classBody, 0, classBody.size) as Class<*>
    }
}
