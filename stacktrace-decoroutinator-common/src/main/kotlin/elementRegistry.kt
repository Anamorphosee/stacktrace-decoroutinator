package dev.reformator.stacktracedecoroutinator.common

import kotlin.coroutines.Continuation

data class DecoroutinatorStacktraceElement(
    val className: String,
    val fileName: String?,
    val methodName: String,
    val lineNumber: Int
)

data class DecoroutinatorContinuationStacktraceElements(
    val continuation2Element: Map<Continuation<*>, DecoroutinatorStacktraceElement>,
    val possibleElements: Set<DecoroutinatorStacktraceElement>
)

interface DecoroutinatorContinuationStacktraceElementRegistry {
    fun getStacktraceElements(continuations: Collection<Continuation<*>>): DecoroutinatorContinuationStacktraceElements
}
