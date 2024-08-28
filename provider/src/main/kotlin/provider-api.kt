@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("DecoroutinatorProviderApiKt")

package dev.reformator.stacktracedecoroutinator.provider

import dev.reformator.bytecodeprocessor.intrinsics.GetOwnerClass
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.provider.internal.provider
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import kotlin.reflect.KClass

const val IS_DECOROUTINATOR_ENABLED_METHOD_NAME = "isDecoroutinatorEnabled"
const val GET_COOKIE_METHOD_NAME = "getCookie"

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
    @JvmName(IS_DECOROUTINATOR_ENABLED_METHOD_NAME) get() = provider.isDecoroutinatorEnabled

val cookie: Any?
    @JvmName(GET_COOKIE_METHOD_NAME) get() = provider.cookie


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

val providerApiClass: Class<*>
    @GetOwnerClass(deleteAfterModification = true) get() { fail() }
