@file:Suppress("PackageDirectoryMismatch")
@file:AndroidKeep

package dev.reformator.stacktracedecoroutinator.mhinvoker.internal

import dev.reformator.bytecodeprocessor.intrinsics.GetOwnerClass
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.provider.internal.AndroidKeep

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
