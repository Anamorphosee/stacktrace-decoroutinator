@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.runtime

import dev.reformator.stacktracedecoroutinator.runtime.internal.FunctionMarker
import dev.reformator.stacktracedecoroutinator.runtime.internal.TransformedClassesSpecRegistry
import dev.reformator.stacktracedecoroutinator.runtime.internal.getFileClass
import dev.reformator.stacktracedecoroutinator.runtime.internal.markedFunctionName
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles

interface DecoroutinatorSpec {
    val lineNumber: Int
    val isLastSpec: Boolean
    val nextHandle: MethodHandle
    val nextSpec: Any
    val coroutineSuspendedMarker: Any
    fun resumeNext(result: Any?): Any?
}

@Target(AnnotationTarget.CLASS)
@Retention
annotation class DecoroutinatorTransformed(
    val fileNamePresent: Boolean = true,
    val fileName: String = "",
    val methodNames: Array<String>,
    val lineNumbersCounts: IntArray,
    val lineNumbers: IntArray,
    val version: Int = 0
)

@FunctionMarker
@Suppress("unused")
fun registerClass(lookup: MethodHandles.Lookup) {
    TransformedClassesSpecRegistry.registerClass(lookup)
}

val registerTransformedFunctionClass = getFileClass()
val registerTransformedFunctionName = registerTransformedFunctionClass.markedFunctionName
