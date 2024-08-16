@file:Suppress("PackageDirectoryMismatch")

import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.runtime.internal.FunctionMarker
import dev.reformator.stacktracedecoroutinator.runtime.internal.getFileClass

internal val unknownSpecClass = getFileClass()

@FunctionMarker
@Suppress("unused")
internal fun unknown(spec: DecoroutinatorSpec, result: Any?): Any? {
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
