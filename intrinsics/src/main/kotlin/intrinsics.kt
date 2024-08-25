@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.intrinsics

import dev.reformator.bytecodeprocessor.intrinsics.fail
import kotlin.coroutines.Continuation

abstract class BaseContinuation: Continuation<Any?> {
    val completion: Continuation<Any?>?
        get() { fail() }

    final override fun resumeWith(result: Result<Any?>) {
        fail()
    }

    init { fail() }
}
