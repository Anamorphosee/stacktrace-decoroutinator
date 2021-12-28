package dev.reformator.stacktracedecoroutinator.utils

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.function.BiFunction

abstract class JavaUtil {
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

val unknownStacktraceMethodHandle: MethodHandle = lookup.findStatic(
    Class.forName("UnknownKt"),
    "unknown",
    invokeStacktraceMethodType
)

inline fun callStacktraceHandles(
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

inline val Any.classLoader: ClassLoader?
    get() = javaClass.classLoader
