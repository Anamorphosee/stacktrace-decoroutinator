@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.runtime

import unknownStacktraceMethodHandle
import java.lang.invoke.MethodHandle
import java.util.function.BiFunction
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.jvm.internal.BaseContinuationImpl

internal fun BaseContinuationImpl.decoroutinatorResumeWith(result: Result<Any?>) {
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
    val stacktraceElements = decoroutinatorRegistry.stacktraceElementRegistry
        .getStacktraceElements(baseContinuations)
    fillStacktraceArrays(baseContinuations, stacktraceElements, stacktraceDepth, stacktraceHandles, stacktraceLineNumbers)
    val invokeCoroutineFunction = BiFunction { index: Int, innerResult: Any? ->
        val continuation = baseContinuations[index]
        JavaUtils().probeCoroutineResumed(continuation)
        val newResult = try {
            val newResult = JavaUtils().invokeSuspend(continuation, Result.success(innerResult))
            if (newResult === COROUTINE_SUSPENDED) {
                return@BiFunction COROUTINE_SUSPENDED
            }
            newResult
        } catch (e: Throwable) {
            JavaUtils().createFailure(e)
        }
        JavaUtils().releaseIntercepted(continuation)
        newResult
    }
    if (result.isFailure && decoroutinatorRegistry.recoveryExplicitStacktrace) {
        val exception = JavaUtils().retrieveResultThrowable(result)
        recoveryExplicitStacktrace(exception, baseContinuations, stacktraceElements)
    }
    val bottomResult = callStacktraceHandles(
        stacktraceHandles = stacktraceHandles,
        lineNumbers = stacktraceLineNumbers,
        nextStepIndex = 0,
        invokeCoroutineFunction = invokeCoroutineFunction,
        result = JavaUtils().retrieveResultValue(result),
        coroutineSuspend = COROUTINE_SUSPENDED
    )
    if (bottomResult === COROUTINE_SUSPENDED) {
        return
    }
    bottomContinuation.resumeWith(Result.success(bottomResult))
}

private fun fillStacktraceArrays(
    baseContinuations: List<BaseContinuationImpl>,
    stacktraceElements: StacktraceElements,
    stacktraceDepth: Int,
    stacktraceHandles: Array<MethodHandle>,
    stacktraceLineNumbers: IntArray
) {
    val stacktraceElement2StacktraceMethodHandle = decoroutinatorRegistry.methodHandleRegistry
        .getStacktraceMethodHandles(stacktraceElements.possibleElements)
    (0 until stacktraceDepth).forEach { index ->
        val continuation = baseContinuations[index]
        val element = stacktraceElements.continuation2Element[continuation]
        if (element != null) {
            stacktraceElement2StacktraceMethodHandle[element]?.let {
                stacktraceHandles[index] = it
            }
            stacktraceLineNumbers[index] = element.lineNumber
        }
    }
}

private fun recoveryExplicitStacktrace(
    exception: Throwable,
    baseContinuations: List<BaseContinuationImpl>,
    stacktraceElements: StacktraceElements
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

