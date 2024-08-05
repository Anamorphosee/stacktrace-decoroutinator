import dev.reformator.stacktracedecoroutinator.runtime.*
import dev.reformator.stacktracedecoroutinator.runtime.FunctionMarker
import dev.reformator.stacktracedecoroutinator.runtime.invokeStacktraceMethodType
import dev.reformator.stacktracedecoroutinator.runtime.lookup
import dev.reformator.stacktracedecoroutinator.runtime.markedFunctionName
import java.lang.invoke.MethodHandle
import java.util.function.BiFunction

internal val unknownStacktraceClass = getFileClass()
internal val unknownStacktraceMethodHandle: MethodHandle = lookup.findStatic(
    unknownStacktraceClass,
    unknownStacktraceClass.markedFunctionName,
    invokeStacktraceMethodType
)

@FunctionMarker
@Suppress("unused")
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
