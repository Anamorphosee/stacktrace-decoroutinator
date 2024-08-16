@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.intrinsics

import kotlin.coroutines.Continuation

@Retention(AnnotationRetention.BINARY)
annotation class ReplaceClassWith(@Suppress("unused") val internalName: String)

@Retention(AnnotationRetention.BINARY)
annotation class Skip


@ReplaceClassWith("kotlin/coroutines/jvm/internal/BaseContinuationImpl")
abstract class BaseContinuation: Continuation<Any?> {
    val completion: Continuation<Any?>?
        get() { failed() }

    init { failed() }
}

@ReplaceClassWith("kotlin/Result\$Failure")
class FailureResult {
    @JvmField val exception: Throwable = failed()

    init { failed() }
}

@ReplaceClassWith("kotlin/coroutines/jvm/internal/DebugProbesKt")
fun probeCoroutineResumed(frame: Continuation<*>) { failed() }

@ReplaceClassWith("kotlin/coroutines/jvm/internal/DebugMetadataKt")
@JvmName("getStackTraceElement")
@Suppress("UnusedReceiverParameter")
fun BaseContinuation.getStackTraceElementImpl(): StackTraceElement? { failed() }

@Suppress("UnusedReceiverParameter")
val Any.toBaseContinuation: BaseContinuation
    @Skip get() { failed() }

@Suppress("UnusedReceiverParameter")
val Result<*>.value: Any?
    @Skip get() { failed() }

@Suppress("UnusedReceiverParameter")
val Any?.toResult: Result<*>
    @Skip get() { failed() }

@Suppress("UNUSED_PARAMETER")
@ReplaceClassWith("kotlin/ResultKt")
fun createFailure(exception: Throwable): Any { failed() }

@ReplaceClassWith("kotlin/coroutines/jvm/internal/DebugMetadata")
@Target(AnnotationTarget.CLASS)
annotation class DebugMetadata(
    @get:JvmName("v")
    val version: Int = 1,
    @get:JvmName("f")
    val sourceFile: String = "",
    @get:JvmName("l")
    val lineNumbers: IntArray = [],
    @get:JvmName("n")
    val localNames: Array<String> = [],
    @get:JvmName("s")
    val spilled: Array<String> = [],
    @get:JvmName("i")
    val indexToLabel: IntArray = [],
    @get:JvmName("m")
    val methodName: String = "",
    @get:JvmName("c")
    val className: String = ""
)

@ReplaceClassWith("kotlin/_Assertions")
object _Assertions {
    @JvmField
    val ENABLED: Boolean = failed()
}

private fun failed(): Nothing = error("intrinsic failed")

