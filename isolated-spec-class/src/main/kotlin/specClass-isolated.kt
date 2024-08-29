@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.isolatedspecclass

import dev.reformator.bytecodeprocessor.intrinsics.DeleteInterface
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import java.lang.invoke.MethodHandle
import java.util.function.Function

@DeleteInterface(DecoroutinatorSpec::class)
class IsolatedSpec(
    override val lineNumber: Int,
    private val _nextSpecHandle: MethodHandle?,
    private val _nextSpec: Any?,
    override val coroutineSuspendedMarker: Any,
    private val resumeNextFunction: Function<Any?, Any?>
): DecoroutinatorSpec {
    override val isLastSpec: Boolean
        get() = _nextSpecHandle == null

    override val nextSpecHandle: MethodHandle
        get() = _nextSpecHandle!!

    override val nextSpec: Any
        get() = _nextSpec!!

    override fun resumeNext(result: Any?): Any? =
        resumeNextFunction.apply(result)
}
