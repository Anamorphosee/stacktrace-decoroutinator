@file:Suppress("PackageDirectoryMismatch")
@file:DecoroutinatorApi

package dev.reformator.stacktracedecoroutinator.mhinvoker.internal

import dev.reformator.bytecodeprocessor.intrinsics.GetOwnerClass
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorApi
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec

internal val unknownSpecClass: Class<*>
    @GetOwnerClass(deleteAfterModification = true) get() = fail()

@Suppress("unused")
internal fun unknown(spec: DecoroutinatorSpec, result: Any?): Any? {
    val updatedResult = if (!spec.isLastSpec) {
        val updatedResult: Any? = spec.nextSpecHandle.invokeExact(spec.nextSpec, result)
        if (updatedResult === spec.coroutineSuspendedMarker) {
            return updatedResult
        }
        updatedResult
    } else {
        result
    }
    return spec.resumeNext(updatedResult)
}
