@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.bytecodeprocessor.intrinsics.GetOwnerClass
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED


internal val unknownSpecClass: Class<*>
    @GetOwnerClass(deleteAfterModification = true) get() = fail()

@Suppress("unused")
internal fun unknown(spec: DecoroutinatorSpec, result: Any?): Any? {
    val updatedResult = if (!spec.isLastSpec) {
        val updatedResult: Any? = spec.nextSpecHandle.invoke(spec.nextSpec, result)
        if (updatedResult === spec.coroutineSuspendedMarker) {
            return updatedResult
        }
        updatedResult
    } else {
        result
    }
    return spec.resumeNext(updatedResult)
}

internal object UnknownSpecMethodsFactory: SpecMethodsFactory {
    private val specMethodHandle = MethodHandles.lookup().findStatic(
        unknownSpecClass,
        ::unknown.name,
        specMethodType
    )

    override fun getSpecAndItsMethodHandle(
        cookie: Cookie,
        element: StacktraceElement,
        nextContinuation: BaseContinuation,
        nextSpec: SpecAndItsMethodHandle?
    ): SpecAndItsMethodHandle =
        SpecAndItsMethodHandle(
            specMethodHandle = specMethodHandle,
            spec = Spec(
                cookie = cookie,
                _nextHandle = nextSpec?.specMethodHandle,
                _nextSpec = nextSpec?.spec,
                nextContinuation = nextContinuation
            )
        )

    private class Spec(
        private val cookie: Cookie,
        private val _nextHandle: MethodHandle?,
        private val _nextSpec: DecoroutinatorSpec?,
        private val nextContinuation: BaseContinuation
    ): DecoroutinatorSpec {
        override val lineNumber: Int
            get() = error("unknown spec doesn't support line numbers")

        override val isLastSpec: Boolean
            get() = _nextHandle == null

        override val nextSpecHandle: MethodHandle
            get() = _nextHandle!!

        override val nextSpec: DecoroutinatorSpec
            get() = _nextSpec!!

        override val coroutineSuspendedMarker: Any
            get() = COROUTINE_SUSPENDED

        override fun resumeNext(result: Any?): Any? =
            nextContinuation.callInvokeSuspend(cookie, result)
    }
}

