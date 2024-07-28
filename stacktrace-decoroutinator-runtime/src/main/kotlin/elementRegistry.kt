@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.runtime

import kotlin.coroutines.Continuation

data class StacktraceElement(
    val className: String,
    val fileName: String?,
    val methodName: String,
    val lineNumber: Int
)

data class StacktraceElements(
    val continuation2Element: Map<Continuation<*>, StacktraceElement>,
    val possibleElements: Set<StacktraceElement>
)

interface StacktraceElementRegistry {
    fun getStacktraceElements(continuations: Collection<Continuation<*>>): StacktraceElements
}
