package dev.reformator.stacktracedecoroutinator.registry

import dev.reformator.stacktracedecoroutinator.DecoroutinatorStacktraceElement
import kotlin.coroutines.Continuation

data class DecoroutinatorContinuationStacktraceElements(
    val continuation2Element: Map<Continuation<*>, DecoroutinatorStacktraceElement>,
    val possibleElements: Set<DecoroutinatorStacktraceElement>
)

interface DecoroutinatorContinuationStacktraceElementRegistry {
    fun getStacktraceElements(continuations: Collection<Continuation<*>>): DecoroutinatorContinuationStacktraceElements
}
