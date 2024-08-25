@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("DecoroutinatorProviderApiKt")

package dev.reformator.stacktracedecoroutinator.provider

import dev.reformator.bytecodeprocessor.intrinsics.GetOwnerClass
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.provider.internal.provider
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
    val version: Int
)

val isDecoroutinatorEnabled: Boolean
    get() = provider.isDecoroutinatorEnabled

val isBaseContinuationPrepared: Boolean
    get() = provider.isBaseContinuationPrepared


fun prepareBaseContinuation(lookup: MethodHandles.Lookup) {
    provider.prepareBaseContinuation(lookup)
}

fun awakeBaseContinuation(baseContinuation: Any, result: Any?) {
    provider.awakeBaseContinuation(baseContinuation, result)
}

fun registerTransformedClass(lookup: MethodHandles.Lookup) {
    provider.registerTransformedClass(lookup)
}

val providerApiClass: Class<*>
    @GetOwnerClass(deleteAfterModification = true) get() { fail() }
