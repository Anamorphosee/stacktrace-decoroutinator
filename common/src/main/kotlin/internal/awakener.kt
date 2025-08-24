@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.common.intrinsics.FailureResult
import dev.reformator.stacktracedecoroutinator.common.intrinsics.toResult
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.math.max

internal fun BaseContinuation.awake(accessor: BaseContinuationAccessor, result: Any?) {
    val stacktraceElements = getStacktraceElements()

    if (recoveryExplicitStacktrace && result.toResult.isFailure) {
        recoveryExplicitStacktrace(
            exception = (result as FailureResult).exception,
            stacktraceElements = stacktraceElements
        )
    }

    if (result === COROUTINE_SUSPENDED) {
        stdlibAwake(
            accessor = accessor,
            result = result
        )
        return
    }

    val newResult = callSpecMethods(
        accessor = accessor,
        stacktraceElements = stacktraceElements,
        result = result
    )
    if (newResult === COROUTINE_SUSPENDED) return

    var baseContinuation = this
    while (true) {
        val nextContinuation = baseContinuation.completion!!
        if (nextContinuation is BaseContinuation) {
            baseContinuation = nextContinuation
        } else {
            nextContinuation.resumeWith(Result.success(newResult))
            break
        }
    }
}

@Suppress("MayBeConstant", "RedundantSuppression")
private val boundaryLabel = "decoroutinator-boundary"
private const val unknown = "unknown"
private val unknownStacktraceElement =
    StackTraceElement("", "", unknown, -1)
private val boundaryStacktraceElement =
    StackTraceElement("", "", boundaryLabel, -1)

private fun BaseContinuation.getStacktraceElements(): List<StackTraceElement?> =
    buildList {
        add(getStackTraceElement())
        var frame = callerFrame
        while (frame != null) {
            add(frame.getStackTraceElement())
            frame = frame.callerFrame
        }
    }

private fun BaseContinuation.stdlibAwake(accessor: BaseContinuationAccessor, result: Any?) {
    var newResult = result
    var baseContinuation = this
    do {
        newResult = baseContinuation.callInvokeSuspend(
            accessor = accessor,
            result = newResult
        )
        if (newResult === COROUTINE_SUSPENDED) return
        baseContinuation = baseContinuation.completion!! as? BaseContinuation ?: break
    } while (true)
    baseContinuation.completion!!.resumeWith(Result.success(newResult))
}

private fun BaseContinuation.callSpecMethods(
    accessor: BaseContinuationAccessor,
    stacktraceElements: List<StackTraceElement?>,
    result: Any?
): Any? {
    val specFactories = specMethodsRegistry.getSpecMethodFactories(
        elements = stacktraceElements.asSequence().drop(1).filterNotNull()
    )
    var specAndMethodHandle: SpecAndMethodHandle? = null
    var baseContinuation: BaseContinuation? = this
    (1 .. stacktraceElements.lastIndex).forEach { index ->
        val element = stacktraceElements[index]
        val factory = element?.let { specFactories[it] }
        @Suppress("IfThenToElvis")
        specAndMethodHandle = if (factory != null) {
            factory.getSpecAndMethodHandle(
                accessor = accessor,
                element = element,
                nextSpec = specAndMethodHandle,
                nextContinuation = baseContinuation
            )
        } else {
            SpecAndMethodHandle(
                specMethodHandle = methodHandleInvoker.unknownSpecMethodHandle,
                spec = DecoroutinatorSpecImpl(
                    accessor = accessor,
                    lineNumber = UNKNOWN_LINE_NUMBER,
                    nextSpecAndItsMethod = specAndMethodHandle,
                    nextContinuation = baseContinuation
                )
            )
        }
        baseContinuation = baseContinuation?.completion as? BaseContinuation
    }

    val newResult = if (specAndMethodHandle != null) {
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION") val specAndMethodHandleCopy = specAndMethodHandle!!
        val newResult = methodHandleInvoker.callSpecMethod(
            handle = specAndMethodHandleCopy.specMethodHandle,
            spec = specAndMethodHandleCopy.spec,
            result = result
        )
        if (newResult === COROUTINE_SUSPENDED) return newResult
        newResult
    } else {
        result
    }

    return if (baseContinuation != null) {
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        baseContinuation!!.callInvokeSuspend(accessor, newResult)
    } else {
        newResult
    }
}

private fun boundaryStackTraceElement(time: UInt): StackTraceElement =
    StackTraceElement("", "", boundaryLabel, time.toInt())

private fun currentTime(): UInt =
    System.currentTimeMillis().toUInt()

private fun recoveryExplicitStacktrace(
    exception: Throwable,
    stacktraceElements: List<StackTraceElement?>
) {
    val trace = exception.stackTrace
    exception.stackTrace = run {
        val boundaryIndex = trace.indexOfFirst { it === boundaryStacktraceElement }
        if (boundaryIndex == -1) {
            return@run Array(trace.size + stacktraceElements.size + 2) {
                if (it < trace.size) {
                    trace[it]
                } else if (it == trace.size) {
                    boundaryStacktraceElement
                } else {
                    val framesIndex = it - trace.size - 1
                    if (framesIndex < stacktraceElements.size) {
                        stacktraceElements[framesIndex] ?: unknownStacktraceElement
                    } else {
                        assert { framesIndex == stacktraceElements.size }
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

        Array(prefixEndIndex + stacktraceElements.size + trace.size - lastBoundaryIndex) {
            if (it < prefixEndIndex) {
                trace[it]
            } else {
                val framesIndex = it - prefixEndIndex
                if (framesIndex < stacktraceElements.size) {
                    stacktraceElements[framesIndex] ?: unknownStacktraceElement
                } else if (framesIndex == stacktraceElements.size) {
                    boundaryStackTraceElement(time)
                } else {
                    val suffixIndex = framesIndex - stacktraceElements.size
                    trace[lastBoundaryIndex + suffixIndex]
                }
            }
        }
    }
}
