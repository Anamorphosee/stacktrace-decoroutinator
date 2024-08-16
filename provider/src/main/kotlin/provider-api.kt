@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.provider

import dev.reformator.stacktracedecoroutinator.provider.internal.DecoroutinatorProvider
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import kotlin.reflect.KClass

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
    val baseContinuationClasses: Array<KClass<*>>,
    val version: Int = 0
)

@Suppress("unused")
fun registerTransformedClass(lookup: MethodHandles.Lookup) {
    DecoroutinatorProvider.instance.registerTransformedClass(lookup)
}
