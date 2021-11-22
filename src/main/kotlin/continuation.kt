package dev.reformator.stacktracedecoroutinator.continuation

import java.lang.invoke.MethodHandle
import java.lang.reflect.Field

internal data class DecoroutinatorContinuationSpec(
    val handle: MethodHandle,
    val labelField: Field,
    val label2LineNumber: Map<Int, Int>
)

@Target(AnnotationTarget.CLASS)
@Retention
internal annotation class DecoroutinatorRuntime
