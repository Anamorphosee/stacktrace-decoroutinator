@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.runtime.internal

import unknownSpecFactory
import java.lang.invoke.MethodHandle
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.jvm.internal.BaseContinuationImpl

internal val awakenerFileClassName = getFileClass().name

internal fun BaseContinuationImpl.awake(result: Result<Any?>) {
    val baseContinuations = buildList {
        var completion: Continuation<Any?> = this@awake
        while (completion is BaseContinuationImpl) {
            add(completion)
            completion = completion.completion!!
        }
    }

    val stacktraceElements = stacktraceElementRegistry.getStacktraceElements(baseContinuations)
    if (result.isFailure && recoveryExplicitStacktrace) {
        val exception = JavaUtils().retrieveResultThrowable(result)
        recoveryExplicitStacktrace(exception, baseContinuations, stacktraceElements)
    }

    val specResult = callSpecMethods(baseContinuations, stacktraceElements, result)
    if (specResult === COROUTINE_SUSPENDED) return

    val lastBaseContinuationResult = baseContinuations.last().callInvokeSuspend(Result.success(specResult))
    if (lastBaseContinuationResult === COROUTINE_SUSPENDED) return

    baseContinuations.last().completion!!.resumeWith(Result.success(lastBaseContinuationResult))
}

private fun callSpecMethods(
    baseContinuations: List<BaseContinuationImpl>,
    stacktraceElements: StacktraceElements,
    result: Result<Any?>
): Any? {
    val specFactories = specRegistry.getSpecFactories(stacktraceElements.possibleElements)
    var prevHandle: MethodHandle? = null
    var prevSpec: Any? = null
    (1 ..< baseContinuations.size).forEach { index ->
        val continuation = baseContinuations[index]
        val element = stacktraceElements.continuation2Element[continuation]
        val factory = element?.let { specFactories[it] } ?: unknownSpecFactory
        val lineNumber = element?.lineNumber ?: 0
        val prevContinuation = baseContinuations[index - 1]
        prevSpec = if (prevHandle != null) {
            factory.createCallingNextHandle(
                lineNumber = lineNumber,
                nextHandle = prevHandle!!,
                nextSpec = prevSpec!!,
                nextContinuation = prevContinuation
            )
        } else {
            factory.createNotCallingNextHandle(
                lineNumber = lineNumber,
                nextContinuation = prevContinuation
            )
        }
        prevHandle = factory.handle
    }
    return if (prevHandle != null) {
        prevHandle!!.invoke(prevSpec, JavaUtils().retrieveResultValue(result))
    } else {
        JavaUtils().retrieveResultValue(result)
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
                val continuation = baseContinuations[it]
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
