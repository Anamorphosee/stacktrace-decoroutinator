@file:Suppress("PackageDirectoryMismatch")

import dev.reformator.stacktracedecoroutinator.runtime.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.runtime.internal.*
import dev.reformator.stacktracedecoroutinator.runtime.internal.FunctionMarker
import dev.reformator.stacktracedecoroutinator.runtime.internal.callSpecMethod
import dev.reformator.stacktracedecoroutinator.runtime.internal.getFileClass

internal val unknownSpecClass = getFileClass()
internal val unknownSpecFactory: SpecFactory = SpecFactoryImpl(lookup.findStatic(
    unknownSpecClass,
    unknownSpecClass.markedFunctionName,
    specMethodType
))

@FunctionMarker
@Suppress("unused")
internal fun unknown(spec: DecoroutinatorSpec, result: Any?) = callSpecMethod(spec, result)
