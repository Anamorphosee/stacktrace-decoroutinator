@file:Suppress("PackageDirectoryMismatch")
@file:DecoroutinatorAndroidKeep

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.bytecodeprocessor.intrinsics.GetOwnerClass
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.common.intrinsics.FailureResult
import dev.reformator.stacktracedecoroutinator.common.intrinsics.toResult
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorAndroidKeep
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorBaseContinuationAccessor
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

internal val awakenerFileClass: Class<*>
    @GetOwnerClass(deleteAfterModification = true) get() = fail()

internal fun BaseContinuation.awake(accessor: DecoroutinatorBaseContinuationAccessor, result: Any?) {
    val baseContinuations = buildList {
        var completion: Continuation<Any?> = this@awake
        while (completion is BaseContinuation) {
            add(completion)
            completion = completion.completion!!
        }
    }

    val stacktraceElements = stacktraceElementsFactory.getStacktraceElements(baseContinuations)
    if (result.toResult.isFailure && recoveryExplicitStacktrace) {
        val exception = (result as FailureResult).exception
        recoveryExplicitStacktrace(exception, baseContinuations, stacktraceElements)
    }

    val specResult = if (baseContinuations.size > 1) {
        callSpecMethods(
            accessor = accessor,
            baseContinuations = baseContinuations,
            stacktraceElements = stacktraceElements,
            result = result
        ).also {
            if (it === COROUTINE_SUSPENDED) return
        }
    } else {
        result
    }

    val lastBaseContinuationResult = baseContinuations.last().callInvokeSuspend(
        accessor = accessor,
        result = specResult
    )
    if (lastBaseContinuationResult === COROUTINE_SUSPENDED) return

    baseContinuations.last().completion!!.resumeWith(Result.success(lastBaseContinuationResult))
}
