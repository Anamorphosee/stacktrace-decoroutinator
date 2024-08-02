@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.runtime

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.function.BiFunction
import kotlin.coroutines.Continuation
import kotlin.coroutines.jvm.internal.BaseContinuationImpl
import kotlin.coroutines.jvm.internal.JavaUtilsImpl

//not getting by reflection because it has not to lead to loading the class
const val BASE_CONTINUATION_CLASS_NAME = "kotlin.coroutines.jvm.internal.BaseContinuationImpl"

inline val Class<*>.isDecoroutinatorBaseContinuation: Boolean
    get() = isAnnotationPresent(DecoroutinatorMarker::class.java)

internal interface JavaUtils {
    fun retrieveResultValue(result: Result<*>): Any?
    fun retrieveResultThrowable(result: Result<*>): Throwable
    fun probeCoroutineResumed(frame: Continuation<*>)
    fun getStackTraceElementImpl(continuation: BaseContinuationImpl): StackTraceElement?
    fun createAwakenerFun(baseContinuations: List<BaseContinuationImpl>): BiFunction<Int, Any?, Any?>

    companion object {
        operator fun invoke(): JavaUtils = impl

        private val impl = JavaUtilsImpl()
    }
}

@Target(AnnotationTarget.CLASS)
@Retention
internal annotation class DecoroutinatorMarker

@Target(AnnotationTarget.FUNCTION)
@Retention
internal annotation class FunctionMarker

internal val Class<*>.markedFunctionName: String
    get() = methods.first { it.isAnnotationPresent(FunctionMarker::class.java) }.name

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

@Suppress("NOTHING_TO_INLINE")
internal inline fun callStacktraceHandles(
    stacktraceHandles: Array<MethodHandle>,
    lineNumbers: IntArray,
    nextStepIndex: Int,
    invokeCoroutineFunction: BiFunction<Int, Any?, Any?>,
    result: Any?,
    coroutineSuspend: Any
): Any? {
    val updatedResult = if (nextStepIndex < stacktraceHandles.size) {
        val updatedResult: Any? = stacktraceHandles[nextStepIndex].invokeExact(
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

internal fun getFileClass(): Class<*> {
    val stacktrace = Exception().stackTrace
    val getFileClassMethodIndex = stacktrace.indexOfFirst {
        it.methodName == "getFileClass"
    }
    if (getFileClassMethodIndex == -1) {
        error("getFileClass invocation is not found")
    }
    return Class.forName(stacktrace[getFileClassMethodIndex + 1].className)
}
