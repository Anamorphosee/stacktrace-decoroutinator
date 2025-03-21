@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.bytecodeprocessor.intrinsics.GetOwnerClass
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.common.intrinsics.FailureResult
import dev.reformator.stacktracedecoroutinator.common.intrinsics.toResult
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

internal val awakenerFileClass: Class<*>
    @GetOwnerClass(deleteAfterModification = true) get() = fail()

internal fun BaseContinuation.awake(cookie: Cookie, result: Any?) {
    val baseContinuations = buildList {
        var completion: Continuation<Any?> = this@awake
        while (completion is BaseContinuation) {
            add(completion)
            completion = completion.completion!!
        }
    }

    val stacktraceElements = stacktraceElementsFactory.getStacktraceElements(baseContinuations.toSet())
    if (result.toResult.isFailure && recoveryExplicitStacktrace) {
        val exception = (result as FailureResult).exception
        recoveryExplicitStacktrace(exception, baseContinuations, stacktraceElements)
    }

    val specResult = if (baseContinuations.size > 1) {
        callSpecMethods(
            cookie = cookie,
            baseContinuations = baseContinuations,
            stacktraceElements = stacktraceElements,
            result = result
        ).also {
            if (it === COROUTINE_SUSPENDED) return
        }
    } else {
        result
    }

    val lastBaseContinuationResult = baseContinuations.last().callInvokeSuspend(cookie, specResult)
    if (lastBaseContinuationResult === COROUTINE_SUSPENDED) return

    baseContinuations.last().completion!!.resumeWith(Result.success(lastBaseContinuationResult))
}

private val unknownStacktraceElement = StacktraceElement(
    lineNumber = UNKNOWN_LINE_NUMBER,
    className = "Unknown",
    methodName = "unknown",
    fileName = null
)

private fun callSpecMethods(
    cookie: Cookie,
    baseContinuations: List<BaseContinuation>,
    stacktraceElements: StacktraceElements,
    result: Any?
): Any? {
    val specFactories = specMethodsRegistry.getSpecMethodFactoriesByStacktraceElement(
        stacktraceElements.possibleElements
    )
    var specAndItsMethodHandle: SpecAndItsMethodHandle? = null
    (1 ..< baseContinuations.size).forEach { index ->
        val continuation = baseContinuations[index]
        val element = stacktraceElements.elementsByContinuation[continuation] ?: unknownStacktraceElement
        val factory = specFactories[element] ?: UnknownSpecMethodsFactory
        val nextContinuation = baseContinuations[index - 1]
        specAndItsMethodHandle = factory.getSpecAndItsMethodHandle(
            cookie = cookie,
            element = element,
            nextSpec = specAndItsMethodHandle,
            nextContinuation = nextContinuation
        )
    }
    return if (specAndItsMethodHandle != null) {
        specAndItsMethodHandle!!.specMethodHandle.invokeExact(specAndItsMethodHandle!!.spec, result)
    } else {
        result
    }
}

private fun recoveryExplicitStacktrace(
    exception: Throwable,
    baseContinuations: List<BaseContinuation>,
    stacktraceElements: StacktraceElements
) {
    val trace = exception.stackTrace
    val traceStartElementIndex = trace.lastIndexOf(decoroutinatorBoundaryStacktraceElement) + 1
    val recoveredStacktrace = Array((trace.size - traceStartElementIndex) + baseContinuations.size + 1) {
        when {
            it < baseContinuations.size -> {
                val continuation = baseContinuations[it]
                val element = stacktraceElements.elementsByContinuation[continuation]
                if (element == null) {
                    artificialFrame("unknown")
                } else {
                    StackTraceElement(element.className, element.methodName, element.fileName, element.lineNumber)
                }
            }
            it == baseContinuations.size -> decoroutinatorBoundaryStacktraceElement
            else -> trace[it - baseContinuations.size - 1 + traceStartElementIndex]
        }
    }
    exception.stackTrace = recoveredStacktrace
}

private fun artificialFrame(message: String) =
    StackTraceElement("", "", message, -1)

private val decoroutinatorBoundaryStacktraceElement = artificialFrame("decoroutinator-boundary")
