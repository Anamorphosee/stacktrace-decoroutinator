@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.common.intrinsics.FailureResult
import dev.reformator.stacktracedecoroutinator.common.intrinsics.toResult
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.intrinsics.UNKNOWN_LINE_NUMBER
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor
import java.lang.invoke.MethodHandle
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.jvm.internal.CoroutineStackFrame
import kotlin.math.max

internal fun BaseContinuation.awake(accessor: BaseContinuationAccessor, result: Any?) {
    val isRecoveryExplicitStacktraceNeeded = recoveryExplicitStacktrace && result.toResult.isFailure

    val stacktraceElements = if (isRecoveryExplicitStacktraceNeeded) getStacktraceElements() else null

    if (isRecoveryExplicitStacktraceNeeded) {
        recoveryExplicitStacktrace(
            exception = (result as FailureResult).exception,
            stacktraceElements = stacktraceElements!!
        )
    }

    if (result === COROUTINE_SUSPENDED) {
        stdlibAwake(
            accessor = accessor,
            result = result
        )
        return
    }

    callSpecMethods(
        accessor = accessor,
        elements = stacktraceElements,
        result = result
    )
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
        add(getNormalizedStackTraceElement())
        var frame = callerFrame
        while (frame != null) {
            add(frame.getNormalizedStackTraceElement())
            frame = frame.callerFrame
        }
    }

private fun CoroutineStackFrame.getNormalizedStackTraceElement(): StackTraceElement? =
    getNormalizedStackTraceElement(getStackTraceElement())

private fun BaseContinuation.getNormalizedStackTraceElement(): StackTraceElement? =
    getNormalizedStackTraceElement(getStackTraceElement())

private fun Any.getNormalizedStackTraceElement(element: StackTraceElement?): StackTraceElement? =
    when {
        element != null -> element
        fillUnknownElementsWithClassName -> StackTraceElement(
            javaClass.name,
            "resumeWith",
            null,
            UNKNOWN_LINE_NUMBER
        )
        else -> null
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
    elements: List<StackTraceElement?>?,
    result: Any?
) {
    var spec: DecoroutinatorSpec? = null
    var specMethod: MethodHandle? = null
    var baseContinuation: BaseContinuation? = this //next
    var elementIndex = 1
    var frame: CoroutineStackFrame? = null //current
    var completion: Continuation<Any?> = this
    while (true) {
        val currentBaseContinuation = baseContinuation
        val element = if (elements != null) {
            if (elementIndex == elements.size) {
                if (currentBaseContinuation != null) {
                    completion = currentBaseContinuation.completion!!
                }
                break
            } else {
                if (currentBaseContinuation != null) {
                    completion = currentBaseContinuation.completion!!
                    baseContinuation = completion as? BaseContinuation
                }
                elements[elementIndex++]
            }
        } else if (currentBaseContinuation != null) {
            val currentCompletion = currentBaseContinuation.completion!!
            if (currentCompletion is BaseContinuation) {
                baseContinuation = currentCompletion
                currentCompletion.getNormalizedStackTraceElement()
            } else {
                completion = currentCompletion
                if (currentCompletion is CoroutineStackFrame) {
                    baseContinuation = null
                    frame = currentCompletion.callerFrame
                    currentCompletion.getNormalizedStackTraceElement()
                } else break
            }
        } else {
            val currentFrame = frame ?: break
            frame = currentFrame.callerFrame
            currentFrame.getNormalizedStackTraceElement()
        }

        @Suppress("IfThenToElvis")
        spec = DecoroutinatorSpecImpl(
            accessor = accessor,
            lineNumber = if (element == null) UNKNOWN_LINE_NUMBER else element.normalizedLineNumber,
            _nextSpec = spec,
            _nextSpecHandle = specMethod,
            nextContinuation = currentBaseContinuation
        )
        specMethod = element?.let { specMethodsFactory.getSpecMethodHandle(it) }
            ?: methodHandleInvoker.unknownSpecMethodHandle
    }

    val specResult = if (spec != null) {
        val specResult = methodHandleInvoker.callSpecMethod(
            handle = specMethod!!,
            spec = spec,
            result = result
        )
        if (specResult === COROUTINE_SUSPENDED) return
        specResult
    } else {
        result
    }

    val baseContinuationResult = if (baseContinuation != null) {
        val baseContinuationResult = baseContinuation.callInvokeSuspend(accessor, specResult)
        if (baseContinuationResult === COROUTINE_SUSPENDED) return
        baseContinuationResult
    } else {
        specResult
    }

    completion.resumeWith(baseContinuationResult.toResult)
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
