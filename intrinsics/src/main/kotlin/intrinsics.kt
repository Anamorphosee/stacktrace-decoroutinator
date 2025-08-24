@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.intrinsics

import dev.reformator.bytecodeprocessor.intrinsics.ChangeClassName
import dev.reformator.bytecodeprocessor.intrinsics.fail
import kotlin.coroutines.Continuation
import kotlin.coroutines.jvm.internal.CoroutineStackFrame

@ChangeClassName(toName = "kotlin.coroutines.jvm.internal.BaseContinuationImpl", deleteAfterChanging = true)
abstract class BaseContinuation: Continuation<Any?>, CoroutineStackFrame {
    @Suppress("RedundantNullableReturnType")
    val completion: Continuation<Any?>?
        get() { fail() }

    final override fun resumeWith(result: Result<Any?>) { fail() }

    init { fail() }

    override fun getStackTraceElement(): StackTraceElement? { fail() }

    abstract fun invokeSuspend(result: Any?): Any?

    open fun releaseIntercepted() { fail() }

    override val callerFrame: CoroutineStackFrame?
        get() = fail()
}

@ChangeClassName(
    toName = "kotlin.coroutines.jvm.internal.DebugMetadata",
    deleteAfterChanging = true
)
@Target(AnnotationTarget.CLASS)
annotation class DebugMetadata(
    val f: String = "",
    val l: IntArray = [],
    val m: String = "",
    val c: String = ""
)
