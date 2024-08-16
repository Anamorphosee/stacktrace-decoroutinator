@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.runtime.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.intrinsics._Assertions
import dev.reformator.stacktracedecoroutinator.intrinsics.createFailure
import dev.reformator.stacktracedecoroutinator.intrinsics.probeCoroutineResumed
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodType
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

//not getting by reflection because it has not to lead to loading the class
const val BASE_CONTINUATION_CLASS_NAME = "kotlin.coroutines.jvm.internal.BaseContinuationImpl"

const val UNKNOWN_LINE_NUMBER = -1

inline fun assert(check: () -> Boolean) {
    if (_Assertions.ENABLED && !check()) {
        throw AssertionError()
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun BaseContinuation.callInvokeSuspend(result: Any?): Any? {
    probeCoroutineResumed(this)
    val newResult = try {
        invokeSuspendHandle!!.invokeExact(this, result)
    } catch (exception: Throwable) {
        return createFailure(exception)
    }
    if (newResult === COROUTINE_SUSPENDED) {
        return newResult
    }
    releaseInterceptedHandle!!.invokeExact(this)
    return newResult
}

internal const val TRANSFORMED_VERSION = 0

@Target(AnnotationTarget.FUNCTION)
@Retention
internal annotation class FunctionMarker

internal class DecoroutinatorSpecImpl(
    override val lineNumber: Int,
    private val nextSpecAndItsMethod: SpecAndItsMethodHandle?,
    private val nextContinuation: BaseContinuation
): DecoroutinatorSpec {
    override val isLastSpec: Boolean
        get() = nextSpecAndItsMethod == null

    override val nextHandle: MethodHandle
        get() = nextSpecAndItsMethod!!.specMethodHandle

    override val nextSpec: Any
        get() = nextSpecAndItsMethod!!.spec

    override val coroutineSuspendedMarker: Any
        get() = COROUTINE_SUSPENDED

    override fun resumeNext(result: Any?): Any? =
        nextContinuation.callInvokeSuspend(result)
}

internal val specMethodType = MethodType.methodType(
    Object::class.java,
    DecoroutinatorSpec::class.java,
    Object::class.java
)

internal val Class<*>.markedFunctionName: String
    get() = methods.first { it.isAnnotationPresent(FunctionMarker::class.java) }.name

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
