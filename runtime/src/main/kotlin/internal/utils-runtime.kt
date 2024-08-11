@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.runtime.internal

import dev.reformator.stacktracedecoroutinator.runtime.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.runtime.DecoroutinatorTransformed
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.jvm.internal.BaseContinuationImpl
import kotlin.coroutines.jvm.internal.JavaUtilsImpl

//not getting by reflection because it has not to lead to loading the class
const val BASE_CONTINUATION_CLASS_NAME = "kotlin.coroutines.jvm.internal.BaseContinuationImpl"

inline val Class<*>.isDecoroutinatorBaseContinuation: Boolean
    get() = isAnnotationPresent(DecoroutinatorMarker::class.java)

val Class<*>.isDecoroutinatorTransformed: Boolean
    get() = getDeclaredAnnotation(DecoroutinatorTransformed::class.java)?.version == TRANSFORMED_VERSION

val lookup: MethodHandles.Lookup = MethodHandles.publicLookup()

fun Continuation<*>.callInvokeSuspend(result: Result<Any?>): Any? =
    (this as BaseContinuationImpl).callInvokeSuspend(result)

internal const val TRANSFORMED_VERSION = 0

internal val specMethodType = MethodType.methodType(
    Object::class.java,
    DecoroutinatorSpec::class.java,
    Object::class.java
)

internal interface JavaUtils {
    fun retrieveResultValue(result: Result<*>): Any?
    fun retrieveResultThrowable(result: Result<*>): Throwable
    fun probeCoroutineResumed(frame: Continuation<*>)
    fun getStackTraceElementImpl(continuation: BaseContinuationImpl): StackTraceElement?
    fun createFailureResult(exception: Throwable): Any
    fun baseContinuationInvokeSuspend(baseContinuation: BaseContinuationImpl, result: Result<Any?>): Any?
    fun baseContinuationReleaseIntercepted(baseContinuation: BaseContinuationImpl)

    companion object {
        private val impl = JavaUtilsImpl()

        operator fun invoke(): JavaUtils = impl
    }
}

internal fun BaseContinuationImpl.callInvokeSuspend(result: Result<Any?>): Any? {
    JavaUtils().probeCoroutineResumed(this)
    val newResult = try {
        JavaUtils().baseContinuationInvokeSuspend(this, result)
    } catch (exception: Throwable) {
        return JavaUtils().createFailureResult(exception)
    }
    if (newResult === COROUTINE_SUSPENDED) {
        return newResult
    }
    JavaUtils().baseContinuationReleaseIntercepted(this)
    return newResult
}

@Target(AnnotationTarget.CLASS)
@Retention
internal annotation class DecoroutinatorMarker

@Target(AnnotationTarget.FUNCTION)
@Retention
internal annotation class FunctionMarker

internal val Class<*>.markedFunctionName: String
    get() = methods.first { it.isAnnotationPresent(FunctionMarker::class.java) }.name

@Suppress("NOTHING_TO_INLINE")
internal inline fun callSpecMethod(spec: DecoroutinatorSpec, result: Any?): Any? {
    val updatedResult = if (!spec.isLastSpec) {
        val updatedResult: Any? = spec.nextHandle.invoke(spec.nextSpec, result)
        if (updatedResult === spec.coroutineSuspendedMarker) {
            return updatedResult
        }
        updatedResult
    } else {
        result
    }
    return spec.resumeNext(updatedResult)
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
