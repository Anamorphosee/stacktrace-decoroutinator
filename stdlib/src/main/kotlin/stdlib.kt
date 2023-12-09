@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.stdlib

import dev.reformator.stacktracedecoroutinator.common.*
import java.lang.invoke.MethodHandle
import java.util.function.BiFunction
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.jvm.internal.BaseContinuationImpl

var invokeSuspendFunc: MethodHandle? = null

internal fun BaseContinuationImpl.decoroutinatorResumeWith(
    result: Result<Any?>,
    invokeSuspendFunc: MethodHandle,
    releaseInterceptedFunc: BaseContinuationImpl.() -> Unit
) {
    val baseContinuations = buildList {
        var completion: Continuation<Any?> = this@decoroutinatorResumeWith
        while (completion is BaseContinuationImpl) {
            add(completion)
            completion = completion.completion!!
        }
        reverse()
    }
    val bottomContinuation = baseContinuations[0].completion!!
    val stacktraceDepth = baseContinuations.lastIndex
    val stacktraceHandles = Array(stacktraceDepth) { unknownStacktraceMethodHandle }
    val stacktraceLineNumbers = IntArray(stacktraceDepth) { -1 }
    val stacktraceElements = decoroutinatorRegistry.continuationStacktraceElementRegistry
        .getStacktraceElements(baseContinuations)
    fillStacktraceArrays(baseContinuations, stacktraceElements, stacktraceDepth, stacktraceHandles, stacktraceLineNumbers)
    val invokeCoroutineFunction = BiFunction { index: Int, result: Any? ->
        val continuation = baseContinuations[index]
        JavaUtilsImpl.probeCoroutineResumed(continuation)
        val newResult = try {
            val newResult = invokeSuspendFunc.invokeExact(continuation, result)
            if (newResult === COROUTINE_SUSPENDED) {
                return@BiFunction COROUTINE_SUSPENDED
            }
            Result.success(newResult)
        } catch (e: Throwable) {
            Result.failure(e)
        }
        continuation.releaseInterceptedFunc()
        JavaUtilsImpl.instance.retrieveResultValue(newResult)
    }
    if (result.isFailure && decoroutinatorRegistry.recoveryExplicitStacktrace) {
        val exception = JavaUtilsImpl.instance.retrieveResultThrowable(result)
        recoveryExplicitStacktrace(exception, baseContinuations, stacktraceElements)
    }
    val bottomResult = callStacktraceHandles(
        stacktraceHandles = stacktraceHandles,
        lineNumbers = stacktraceLineNumbers,
        nextStepIndex = 0,
        invokeCoroutineFunction = invokeCoroutineFunction,
        result = JavaUtilsImpl.instance.retrieveResultValue(result),
        coroutineSuspend = COROUTINE_SUSPENDED
    )
    if (bottomResult === COROUTINE_SUSPENDED) {
        return
    }
    bottomContinuation.resumeWith(Result.success(bottomResult))
}

private fun fillStacktraceArrays(
    baseContinuations: List<BaseContinuationImpl>,
    stacktraceElements: DecoroutinatorContinuationStacktraceElements,
    stacktraceDepth: Int,
    stacktraceHandles: Array<MethodHandle>,
    stacktraceLineNumbers: IntArray
) {
    val stacktraceElement2StacktraceMethodHandle = decoroutinatorRegistry.stacktraceMethodHandleRegistry
        .getStacktraceMethodHandles(stacktraceElements.possibleElements)
    (0 until stacktraceDepth).forEach { index ->
        val continuation = baseContinuations[index]
        val element = stacktraceElements.continuation2Element[continuation]
        if (element != null) {
            stacktraceHandles[index] = stacktraceElement2StacktraceMethodHandle[element]!!
            stacktraceLineNumbers[index] = element.lineNumber
        }
    }
}

private fun recoveryExplicitStacktrace(
    exception: Throwable,
    baseContinuations: List<BaseContinuationImpl>,
    stacktraceElements: DecoroutinatorContinuationStacktraceElements
) {
    val recoveredStacktrace = Array(exception.stackTrace.size + baseContinuations.size + 1) {
        when {
            it < baseContinuations.size -> {
                val continuation = baseContinuations[baseContinuations.lastIndex - it]
                val element = stacktraceElements.continuation2Element[continuation]
                if (element == null) {
                    artificialFrame("unknown")
                } else {
                    StackTraceElement(element.className, element.methodName, element.fileName, element.lineNumber)
                }
            }
            it == baseContinuations.size -> artificialFrame("boundary")
            else -> exception.stackTrace[it - baseContinuations.size - 1]
        }
    }
    exception.stackTrace = recoveredStacktrace
}

private fun artificialFrame(message: String) =
    StackTraceElement("", "", message, -1)

