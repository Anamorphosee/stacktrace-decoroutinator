@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.common.intrinsics._Assertions
import dev.reformator.stacktracedecoroutinator.common.intrinsics.createFailure
import dev.reformator.stacktracedecoroutinator.common.intrinsics.probeCoroutineResumed
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorTransformed
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodType
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

const val BASE_CONTINUATION_CLASS_NAME = "kotlin.coroutines.jvm.internal.BaseContinuationImpl"
const val DEBUG_METADATA_CLASS_NAME = "kotlin.coroutines.jvm.internal.DebugMetadata"

const val TRANSFORMED_VERSION = 0

const val UNKNOWN_LINE_NUMBER = -1

inline fun assert(check: () -> Boolean) {
    if (_Assertions.ENABLED && !check()) {
        throw AssertionError()
    }
}

fun BaseContinuation.publicCallInvokeSuspend(result: Any?): Any? =
    callInvokeSuspend(result)

val Class<*>.isTransformed: Boolean
    get() {
        val transformed = getDeclaredAnnotation(DecoroutinatorTransformed::class.java) ?: return false
        if (transformed.version > TRANSFORMED_VERSION) {
            error("Class [$this] has transformed meta of version [${transformed.version}]. Please update Decoroutinator")
        }
        return transformed.version == TRANSFORMED_VERSION
    }

internal const val ENABLED_PROPERTY = "dev.reformator.stacktracedecoroutinator.enabled"

@Suppress("NOTHING_TO_INLINE")
internal inline fun BaseContinuation.callInvokeSuspend(result: Any?): Any? {
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
