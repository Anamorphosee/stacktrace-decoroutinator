@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.intrinsics

import dev.reformator.bytecodeprocessor.intrinsics.ChangeClassName
import dev.reformator.bytecodeprocessor.intrinsics.fail
import kotlin.coroutines.Continuation

@ChangeClassName(toName = "kotlin.coroutines.jvm.internal.BaseContinuationImpl", deleteAfterChanging = true)
abstract class BaseContinuation: Continuation<Any?> {
    val completion: Continuation<Any?>?
        get() { fail() }

    final override fun resumeWith(result: Result<Any?>) {
        fail()
    }

    init { fail() }
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
