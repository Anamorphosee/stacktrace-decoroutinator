package dev.reformator.stacktracedecoroutinator.common

import java.lang.invoke.MethodHandle
import java.util.function.BiFunction

internal fun unknown(
    stacktraceHandles: Array<MethodHandle>,
    lineNumbers: IntArray,
    nextStepIndex: Int,
    invokeCoroutineFunction: BiFunction<Int, Any?, Any?>,
    result: Any?,
    coroutineSuspend: Any
) = callStacktraceHandles(
    stacktraceHandles = stacktraceHandles,
    lineNumbers = lineNumbers,
    nextStepIndex = nextStepIndex,
    invokeCoroutineFunction = invokeCoroutineFunction,
    result = result,
    coroutineSuspend = coroutineSuspend
)
