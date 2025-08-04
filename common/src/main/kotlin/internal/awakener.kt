@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.common.intrinsics.FailureResult
import dev.reformator.stacktracedecoroutinator.common.intrinsics.toResult
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor
import kotlin.collections.get
import kotlin.coroutines.jvm.internal.CoroutineStackFrame
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.math.max

internal fun BaseContinuation.awake(accessor: BaseContinuationAccessor, result: Any?) {
    val frames = getFrames()

    val baseContinuationsEndIndex = getBaseContinuationsEndIndex(frames)

    val elementsByFrameIndex = getElementsByFrameIndex(
        frames = frames,
        baseContinuationsEndIndex = baseContinuationsEndIndex
    )

    if (recoveryExplicitStacktrace && result.toResult.isFailure) {
        recoveryExplicitStacktrace(
            exception = (result as FailureResult).exception,
            elementsByFrameIndex = elementsByFrameIndex
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
        frames = frames,
        baseContinuationsEndIndex = baseContinuationsEndIndex,
        elementsByFrameIndex = elementsByFrameIndex,
        result = result
    )
    if (newResult === COROUTINE_SUSPENDED) return

    (frames[baseContinuationsEndIndex - 1] as BaseContinuation).completion!!.resumeWith(Result.success(newResult))
}

@Suppress("MayBeConstant", "RedundantSuppression")
private val boundaryLabel = "decoroutinator-boundary"
private const val unknown = "unknown"
private val unknownStacktraceElement =
    StackTraceElement("", "", unknown, -1)
private val boundaryStacktraceElement =
    StackTraceElement("", "", boundaryLabel, -1)

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

private fun BaseContinuation.getFrames(): List<CoroutineStackFrame> =
    buildList {
        add(this@getFrames)
        var frame = callerFrame
        while (if (restoreCoroutineStackFrames) frame != null else frame is BaseContinuation) {
            add(frame!!)
            frame = frame.callerFrame
        }
    }

private fun getBaseContinuationsEndIndex(
    frames: List<CoroutineStackFrame>
): Int =
    if (restoreCoroutineStackFrames) {
        var baseContinuationsEndIndex = 1
        while (baseContinuationsEndIndex < frames.size && frames[baseContinuationsEndIndex] is BaseContinuation) {
            baseContinuationsEndIndex++
        }
        baseContinuationsEndIndex
    } else {
        frames.size
    }

private fun getElementsByFrameIndex(
    frames: List<CoroutineStackFrame>,
    baseContinuationsEndIndex: Int
): Array<StackTraceElement?> {
    val elementsByBaseContinuation = if (baseContinuationsEndIndex == frames.size) {
        @Suppress("UNCHECKED_CAST")
        stacktraceElementsFactory.getStacktraceElements(frames.asSequence() as Sequence<BaseContinuation>)
    } else {
        val baseContinuationsSeq = frames.indices.asSequence().mapNotNull { index ->
            val frame = frames[index]
            if (index < baseContinuationsEndIndex) {
                frame as BaseContinuation
            } else {
                frame as? BaseContinuation
            }
        }
        stacktraceElementsFactory.getStacktraceElements(baseContinuationsSeq)
    }
    return Array(frames.size) { index ->
        val frame = frames[index]
        val element = when {
            index < baseContinuationsEndIndex -> elementsByBaseContinuation[frame]
            frame is BaseContinuation -> elementsByBaseContinuation[frame]
            else -> frame.getStackTraceElement()
        }
        if (fillUnknownElementsWithClassName && element == null) {
            StackTraceElement(
                frame.javaClass.name,
                unknown,
                unknown,
                UNKNOWN_LINE_NUMBER
            )
        } else {
            element
        }
    }
}


private fun callSpecMethods(
    accessor: BaseContinuationAccessor,
    frames: List<CoroutineStackFrame>,
    baseContinuationsEndIndex: Int,
    elementsByFrameIndex: Array<StackTraceElement?>,
    result: Any?
): Any? {
    assert { frames.size == elementsByFrameIndex.size }
    assert { baseContinuationsEndIndex <= frames.size }
    val specFactories = specMethodsRegistry.getSpecMethodFactories(
        elements = elementsByFrameIndex.asSequence().drop(1).filterNotNull()
    )
    var specAndMethodHandle: SpecAndMethodHandle? = null
    (1 .. frames.lastIndex).forEach { index ->
        val element = elementsByFrameIndex[index]
        val factory = element?.let { specFactories[it] }
        val nextContinuation = if (index <= baseContinuationsEndIndex) {
            frames[index - 1] as BaseContinuation
        } else {
            null
        }
        @Suppress("IfThenToElvis")
        specAndMethodHandle = if (factory != null) {
            factory.getSpecAndMethodHandle(
                accessor = accessor,
                element = element,
                nextSpec = specAndMethodHandle,
                nextContinuation = nextContinuation
            )
        } else {
            SpecAndMethodHandle(
                specMethodHandle = methodHandleInvoker.unknownSpecMethodHandle,
                spec = DecoroutinatorSpecImpl(
                    accessor = accessor,
                    lineNumber = UNKNOWN_LINE_NUMBER,
                    nextSpecAndItsMethod = specAndMethodHandle,
                    nextContinuation = nextContinuation
                )
            )
        }
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

    return if (baseContinuationsEndIndex == frames.size) {
        (frames[baseContinuationsEndIndex - 1] as BaseContinuation).callInvokeSuspend(
            accessor = accessor,
            result = newResult
        )
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
    elementsByFrameIndex: Array<StackTraceElement?>
) {
    val trace = exception.stackTrace
    exception.stackTrace = run {
        val boundaryIndex = trace.indexOfFirst { it === boundaryStacktraceElement }
        if (boundaryIndex == -1) {
            return@run Array(trace.size + elementsByFrameIndex.size + 2) {
                if (it < trace.size) {
                    trace[it]
                } else if (it == trace.size) {
                    boundaryStacktraceElement
                } else {
                    val framesIndex = it - trace.size - 1
                    if (framesIndex < elementsByFrameIndex.size) {
                        elementsByFrameIndex[framesIndex] ?: unknownStacktraceElement
                    } else {
                        assert { framesIndex == elementsByFrameIndex.size }
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

        Array(prefixEndIndex + elementsByFrameIndex.size + trace.size - lastBoundaryIndex) {
            if (it < prefixEndIndex) {
                trace[it]
            } else {
                val framesIndex = it - prefixEndIndex
                if (framesIndex < elementsByFrameIndex.size) {
                    elementsByFrameIndex[framesIndex] ?: unknownStacktraceElement
                } else if (framesIndex == elementsByFrameIndex.size) {
                    boundaryStackTraceElement(time)
                } else {
                    val suffixIndex = framesIndex - elementsByFrameIndex.size
                    trace[lastBoundaryIndex + suffixIndex]
                }
            }
        }
    }
}
