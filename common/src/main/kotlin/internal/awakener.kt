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

    val stacktraceElements = stacktraceElementsFactory.getStacktraceElements(baseContinuations)
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

    val lastBaseContinuationResult = methodHandleInvoker.callInvokeSuspend(
        continuation = baseContinuations.last(),
        cookie = cookie,
        specResult = specResult
    )
    if (lastBaseContinuationResult === COROUTINE_SUSPENDED) return

    baseContinuations.last().completion!!.resumeWith(Result.success(lastBaseContinuationResult))
}

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
        val element = stacktraceElements.elementsByContinuation[continuation]
        val factory = specFactories[element]
        val nextContinuation = baseContinuations[index - 1]
        specAndItsMethodHandle = if (element != null && factory != null) {
            factory.getSpecAndItsMethodHandle(
                cookie = cookie,
                element = element,
                nextSpec = specAndItsMethodHandle,
                nextContinuation = nextContinuation
            )
        } else {
            SpecAndItsMethodHandle(
                specMethodHandle = methodHandleInvoker.unknownSpecMethodHandle,
                spec = methodHandleInvoker.createSpec(
                    cookie = cookie,
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

private fun recoveryExplicitStacktrace(
    exception: Throwable,
    baseContinuations: List<BaseContinuation>,
    stacktraceElements: StacktraceElements
) {
    val trace = exception.stackTrace
    val traceSize = trace.size
    var prefixBoundaryIndex = 0
    var prefixBoundaryTime = NOT_BOUNDARY_FRAME_TIME
    while (prefixBoundaryIndex < traceSize) {
        prefixBoundaryTime = trace[prefixBoundaryIndex].boundaryFrameTime
        if (prefixBoundaryTime != NOT_BOUNDARY_FRAME_TIME) break
        prefixBoundaryIndex++
    }
    val baseContinuationBoundaryIndex = prefixBoundaryIndex + 1 + baseContinuations.size
    val time = System.currentTimeMillis()
    val recoveredTraceSize = if (prefixBoundaryTime > time - recoveryExplicitStacktraceTimeoutMs) {
        traceSize + 1 + baseContinuations.size
    } else {
        baseContinuationBoundaryIndex
    }
    val recoveredTrace = Array(recoveredTraceSize) {
        when {
            it < prefixBoundaryIndex -> trace[it]
            it == prefixBoundaryIndex -> boundaryFrame(time)
            it < baseContinuationBoundaryIndex -> {
                val continuation = baseContinuations[it - prefixBoundaryIndex - 1]
                val element = stacktraceElements.elementsByContinuation[continuation]
                if (element == null) {
                    unknownStacktraceElement
                } else {
                    StackTraceElement(
                        element.className,
                        element.methodName,
                        element.fileName,
                        element.lineNumber
                    )
                }
            }
            else -> trace[it - baseContinuationBoundaryIndex + prefixBoundaryIndex]
        }
    }
    exception.stackTrace = recoveredTrace
}

private fun artificialFrame(message: String) =
    StackTraceElement("", "", message, -1)

private const val BOUNDARY_LABEL = "decoroutinator-boundary-"
private const val BOUNDARY_LABEL_LENGTH = BOUNDARY_LABEL.length
private const val NOT_BOUNDARY_FRAME_TIME = -1L

private val unknownStacktraceElement = artificialFrame("unknown")

private fun boundaryFrame(time: Long) = artificialFrame(BOUNDARY_LABEL + time)

private val StackTraceElement.boundaryFrameTime: Long
    get() {
        if (className.isNotEmpty()) return NOT_BOUNDARY_FRAME_TIME
        val fileName = fileName
        if (fileName == null || !fileName.startsWith(BOUNDARY_LABEL)) return NOT_BOUNDARY_FRAME_TIME
        return try {
            fileName.substringToLong(BOUNDARY_LABEL_LENGTH)
        } catch (_: NumberFormatException) {
            NOT_BOUNDARY_FRAME_TIME
        }
    }

private fun String.substringToLong(beginIndex: Int = 0, endIndex: Int = length, radix: Int = 10): Long =
    if (supportsParseLongSubstring) {
        java.lang.Long.parseLong(this, beginIndex, endIndex, radix)
    } else {
        substring(beginIndex, endIndex).toLong(radix)
    }

private val supportsParseLongSubstring = try {
    val str = "123"
    java.lang.Long.parseLong(str, 0, str.length, 10)
    true
} catch (_: Throwable) { false }
