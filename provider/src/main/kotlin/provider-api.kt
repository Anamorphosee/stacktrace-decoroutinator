@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("DecoroutinatorProviderApiKt")
@file:DecoroutinatorTransformed(
    methodNames = [],
    lineNumbersCounts = [],
    lineNumbers = [],
    baseContinuationClasses = [],
    marker = true
)

package dev.reformator.stacktracedecoroutinator.provider

import dev.reformator.bytecodeprocessor.intrinsics.GetOwnerClass
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.provider.internal.provider
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles

@DecoroutinatorTransformed(
    methodNames = [],
    lineNumbersCounts = [],
    lineNumbers = [],
    baseContinuationClasses = [],
    marker = true
)
interface DecoroutinatorSpec {
    val lineNumber: Int
    val isLastSpec: Boolean
    val nextSpecHandle: MethodHandle
    val nextSpec: DecoroutinatorSpec
    val coroutineSuspendedMarker: Any
    fun resumeNext(result: Any?): Any?
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
@Retention
annotation class DecoroutinatorTransformed(
    val fileNamePresent: Boolean = true,
    val fileName: String = "",
    val methodNames: Array<String>,
    val lineNumbersCounts: IntArray,
    val lineNumbers: IntArray,
    val baseContinuationClasses: Array<String>,
    val skipSpecMethods: Boolean = false,
    val marker: Boolean = false
)

val isDecoroutinatorEnabled: Boolean
    get() = provider.isDecoroutinatorEnabled

val cookie: Any?
    get() = provider.cookie

fun prepareCookie(lookup: MethodHandles.Lookup): Any =
    provider.prepareCookie(lookup)

fun awakeBaseContinuation(cookie: Any, baseContinuation: Any, result: Any?) {
    provider.awakeBaseContinuation(
        cookie = cookie,
        baseContinuation = baseContinuation,
        result = result
    )
}

fun registerTransformedClass(lookup: MethodHandles.Lookup) {
    provider.registerTransformedClass(lookup)
}

fun getBaseContinuation(
    completion: Any?,
    fileName: String?,
    className: String,
    methodName: String,
    lineNumber: Int
): Any? =
    provider.getBaseContinuation(
        completion = completion,
        fileName = fileName,
        className = className,
        methodName = methodName,
        lineNumber = lineNumber
    )

val providerApiClass: Class<*>
    @GetOwnerClass(deleteAfterModification = true) get() { fail() }
