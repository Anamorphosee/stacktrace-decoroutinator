@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.mhinvoker.internal

import dev.reformator.bytecodeprocessor.intrinsics.ChangeInvocationsOwner
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.common.internal.Cookie
import dev.reformator.stacktracedecoroutinator.common.internal.MethodHandleInvoker
import dev.reformator.stacktracedecoroutinator.common.internal.SpecAndItsMethodHandle
import dev.reformator.stacktracedecoroutinator.common.internal.specMethodType
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

internal class RegularMethodHandleInvoker: MethodHandleInvoker {
    override fun createSpec(
        cookie: Cookie,
        lineNumber: Int,
        nextSpecAndItsMethod: SpecAndItsMethodHandle?,
        nextContinuation: BaseContinuation
    ): DecoroutinatorSpec =
        DecoroutinatorSpecImpl(
            cookie = cookie,
            lineNumber = lineNumber,
            nextSpecAndItsMethod = nextSpecAndItsMethod,
            nextContinuation = nextContinuation
        )

    override val unknownSpecMethodHandle: MethodHandle =
        MethodHandles.lookup().findStatic(
            unknownSpecClass,
            ::unknown.name,
            specMethodType
        )

    override fun callInvokeSuspend(continuation: BaseContinuation, cookie: Cookie, specResult: Any?): Any? =
        continuation.callInvokeSuspend(cookie, specResult)

    override fun callSpecMethod(handle: MethodHandle, spec: DecoroutinatorSpec, result: Any?): Any? =
        handle.invokeExact(spec, result)

    override val unknownSpecMethodClass: Class<*> = unknownSpecClass
}

internal class DecoroutinatorSpecImpl(
    private val cookie: Cookie,
    override val lineNumber: Int,
    private val nextSpecAndItsMethod: SpecAndItsMethodHandle?,
    private val nextContinuation: BaseContinuation
): DecoroutinatorSpec {
    override val isLastSpec: Boolean
        get() = nextSpecAndItsMethod == null

    override val nextSpecHandle: MethodHandle
        get() = nextSpecAndItsMethod!!.specMethodHandle

    override val nextSpec: DecoroutinatorSpec
        get() = nextSpecAndItsMethod!!.spec

    override val coroutineSuspendedMarker: Any
        get() = COROUTINE_SUSPENDED

    override fun resumeNext(result: Any?): Any? =
        nextContinuation.callInvokeSuspend(cookie, result)
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun BaseContinuation.callInvokeSuspend(cookie: Cookie, result: Any?): Any? {
    probeCoroutineResumed(this)
    val newResult = try {
        cookie.invokeSuspendHandle.invokeExact(this, result)
    } catch (exception: Throwable) {
        return createFailure(exception)
    }
    if (newResult === COROUTINE_SUSPENDED) {
        return newResult
    }
    cookie.releaseInterceptedHandle.invokeExact(this)
    return newResult
}

@Suppress("UNUSED_PARAMETER")
@ChangeInvocationsOwner(
    toName = "kotlin.ResultKt",
    deleteAfterChanging = true
)
@PublishedApi
internal fun createFailure(exception: Throwable): Any { fail() }

@Suppress("UNUSED_PARAMETER")
@ChangeInvocationsOwner(
    toName = "kotlin.coroutines.jvm.internal.DebugProbesKt",
    deleteAfterChanging = true
)
@PublishedApi
internal fun probeCoroutineResumed(frame: Continuation<*>) { fail() }
