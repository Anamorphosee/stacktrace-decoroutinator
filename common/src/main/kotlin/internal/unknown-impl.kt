@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import unknown
import unknownSpecClass
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

internal object UnknownSpecMethodsFactory: SpecMethodsFactory {
    private val specMethodHandle = MethodHandles.lookup().findStatic(
        unknownSpecClass,
        ::unknown.name,
        specMethodType
    )

    override fun getSpecAndItsMethodHandle(
        element: StacktraceElement,
        nextContinuation: BaseContinuation,
        nextSpec: SpecAndItsMethodHandle?
    ): SpecAndItsMethodHandle =
        SpecAndItsMethodHandle(
            specMethodHandle = specMethodHandle,
            spec = Spec(
                _nextHandle = nextSpec?.specMethodHandle,
                _nextSpec = nextSpec?.spec,
                nextContinuation = nextContinuation
            )
        )

    private class Spec(
        private val _nextHandle: MethodHandle?,
        private val _nextSpec: Any?,
        private val nextContinuation: BaseContinuation
    ): DecoroutinatorSpec {
        override val lineNumber: Int
            get() = error("unknown spec doesn't support line numbers")

        override val isLastSpec: Boolean
            get() = _nextHandle == null

        override val nextHandle: MethodHandle
            get() = _nextHandle!!

        override val nextSpec: Any
            get() = _nextSpec!!

        override val coroutineSuspendedMarker: Any
            get() = COROUTINE_SUSPENDED

        override fun resumeNext(result: Any?): Any? =
            nextContinuation.callInvokeSuspend(result)
    }
}
