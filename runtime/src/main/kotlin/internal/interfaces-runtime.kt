@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.runtime.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import java.lang.invoke.MethodHandle

data class StacktraceElement(
    val className: String,
    val fileName: String?,
    val methodName: String,
    val lineNumber: Int,
)

fun interface SpecMethodsRegistry {
    fun getSpecMethodFactoriesByStacktraceElement(
        elements: Set<StacktraceElement>
    ): Map<StacktraceElement, SpecMethodsFactory>
}

data class SpecAndItsMethodHandle(
    val specMethodHandle: MethodHandle,
    val spec: Any
)

fun interface SpecMethodsFactory {
    fun getSpecAndItsMethodHandle(
        element: StacktraceElement,
        nextContinuation: BaseContinuation,
        nextSpec: SpecAndItsMethodHandle?
    ): SpecAndItsMethodHandle
}

internal data class StacktraceElements(
    val elementsByContinuation: Map<BaseContinuation, StacktraceElement>,
    val possibleElements: Set<StacktraceElement>
)

internal fun interface StacktraceElementsFactory {
    fun getStacktraceElements(continuations: Set<BaseContinuation>): StacktraceElements
}
