@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.common.intrinsics.FailureResult
import dev.reformator.stacktracedecoroutinator.common.intrinsics.toResult
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.math.max

internal fun BaseContinuation.awake(accessor: BaseContinuationAccessor, result: Any?) {
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

@Suppress("MayBeConstant", "RedundantSuppression")
private val boundaryLabel = "decoroutinator-boundary"
private val unknownStacktraceElement =
    StackTraceElement("", "", "unknown", -1)
private val boundaryStacktraceElement =
    StackTraceElement("", "", boundaryLabel, -1)

private fun callSpecMethods(
    accessor: BaseContinuationAccessor,
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
        val element = stacktraceElements.elementsByContinuation[continuation]
        val factory = specFactories[element]
        val nextContinuation = baseContinuations[index - 1]
        specAndItsMethodHandle = if (element != null && factory != null) {
            factory.getSpecAndItsMethodHandle(
                accessor = accessor,
                element = element,
                nextSpec = specAndItsMethodHandle,
                nextContinuation = nextContinuation
            )
        } else {
            SpecAndItsMethodHandle(
                specMethodHandle = methodHandleInvoker.unknownSpecMethodHandle,
                spec = DecoroutinatorSpecImpl(
                    accessor = accessor,
                    lineNumber = UNKNOWN_LINE_NUMBER,
                    nextSpecAndItsMethod = specAndItsMethodHandle,
                    nextContinuation = nextContinuation
                )
            )
        }
    }
    return specAndItsMethodHandle.let {
        if (it != null) {
            methodHandleInvoker.callSpecMethod(
                handle = it.specMethodHandle,
                spec = it.spec,
                result = result
            )
        } else {
            result
        }
    }
}

private val StacktraceElement?.java: StackTraceElement
    get() = if (this == null) {
        unknownStacktraceElement
    } else {
        StackTraceElement(
            this.className,
            this.methodName,
            this.fileName,
            this.lineNumber
        )
    }

private fun boundaryStackTraceElement(time: UInt): StackTraceElement =
    StackTraceElement("", "", boundaryLabel, time.toInt())

private fun currentTime(): UInt =
    System.currentTimeMillis().toUInt()

private fun recoveryExplicitStacktrace(
    exception: Throwable,
    baseContinuations: List<BaseContinuation>,
    stacktraceElements: StacktraceElements
) {
    val trace = exception.stackTrace
    exception.stackTrace = run {
        val boundaryIndex = trace.indexOfFirst { it === boundaryStacktraceElement }
        if (boundaryIndex == -1) {
            return@run Array(trace.size + baseContinuations.size + 2) {
                if (it < trace.size) {
                    trace[it]
                } else if (it == trace.size) {
                    boundaryStacktraceElement
                } else {
                    val continuationIndex = it - trace.size - 1
                    if (continuationIndex < baseContinuations.size) {
                        val continuation = baseContinuations[continuationIndex]
                        val element = stacktraceElements.elementsByContinuation[continuation]
                        element.java
                    } else {
                        assert { continuationIndex == baseContinuations.size }
                        boundaryStackTraceElement(currentTime())
                    }
                }
            }
        }

        val lastBoundaryIndex = max(
            trace.indexOfLast { it.className.isEmpty() && it.methodName.isEmpty() && it.fileName === boundaryLabel },
            boundaryIndex
        )
        val time = currentTime()
        val erasePreviousBoundaries = lastBoundaryIndex > boundaryIndex &&
                time > recoveryExplicitStacktraceTimeoutMs &&
                trace[lastBoundaryIndex].lineNumber.toUInt() < time - recoveryExplicitStacktraceTimeoutMs
        val prefixEndIndex = (if (erasePreviousBoundaries) boundaryIndex else lastBoundaryIndex) + 1

        Array(prefixEndIndex + baseContinuations.size + trace.size - lastBoundaryIndex) {
            if (it < prefixEndIndex) {
                trace[it]
            } else {
                val baseContinuationIndex = it - prefixEndIndex
                if (baseContinuationIndex < baseContinuations.size) {
                    val continuation = baseContinuations[baseContinuationIndex]
                    val element = stacktraceElements.elementsByContinuation[continuation]
                    element.java
                } else if (baseContinuationIndex == baseContinuations.size) {
                    boundaryStackTraceElement(time)
                } else {
                    val suffixIndex = baseContinuationIndex - baseContinuations.size
                    trace[lastBoundaryIndex + suffixIndex]
                }
            }
        }
    }
}
