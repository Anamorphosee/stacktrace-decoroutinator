package kotlin.coroutines.jvm.internal

import dev.reformator.stacktracedecoroutinator.continuation.DecoroutinatorRuntimeMarker
import dev.reformator.stacktracedecoroutinator.registry.DecoroutinatorContinuationStacktraceElements
import dev.reformator.stacktracedecoroutinator.registry.decoroutinatorRegistry
import dev.reformator.stacktracedecoroutinator.utils.JavaUtilImpl
import dev.reformator.stacktracedecoroutinator.utils.callStacktraceHandles
import dev.reformator.stacktracedecoroutinator.utils.unknownStacktraceMethodHandle
import java.io.Serializable
import java.lang.invoke.MethodHandle
import java.util.function.BiFunction
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

@DecoroutinatorRuntimeMarker
internal abstract class BaseContinuationImpl(
    val completion: Continuation<Any?>?
): Continuation<Any?>, CoroutineStackFrame, Serializable {
    final override fun resumeWith(result: Result<Any?>) {
        if (decoroutinatorRegistry.enabled) {
            decoroutinatorResumeWith(result)
        } else {
            stdlibResumeWith(result)
        }
    }

    private fun decoroutinatorResumeWith(result: Result<Any?>) {
        val baseContinuations = buildList {
            var completion: Continuation<Any?> = this@BaseContinuationImpl
            while (completion is BaseContinuationImpl) {
                add(completion)
                completion = completion.completion!!
            }
            reverse()
        }
        val bottomContinuation = baseContinuations[0].completion!!
        val stacktraceDepth = baseContinuations.lastIndex
        val stacktraceHandles = Array<MethodHandle?>(stacktraceDepth) { null } as Array<MethodHandle>
        val stacktraceLineNumbers = IntArray(stacktraceDepth)
        val stacktraceElements = decoroutinatorRegistry.continuationStacktraceElementRegistry
            .getStacktraceElements(baseContinuations)
        fillStacktraceArrays(baseContinuations, stacktraceElements, stacktraceDepth, stacktraceHandles, stacktraceLineNumbers)
        val invokeCoroutineFunction = BiFunction { index: Int, result: Any? ->
            val continuation = baseContinuations[index]
            JavaUtilImpl.probeCoroutineResumed(continuation)
            val newResult = try {
                val newResult = continuation.invokeSuspend(Result.success(result))
                if (newResult === COROUTINE_SUSPENDED) {
                    return@BiFunction COROUTINE_SUSPENDED
                }
                Result.success(newResult)
            } catch (e: Throwable) {
                Result.failure(e)
            }
            continuation.releaseIntercepted()
            JavaUtilImpl.instance.retrieveResultValue(newResult)
        }
        if (result.isFailure && decoroutinatorRegistry.recoveryExplicitStacktrace) {
            val exception = JavaUtilImpl.instance.retrieveResultThrowable(result)
            recoveryExplicitStacktrace(exception, baseContinuations, stacktraceElements)
        }
        val bottomResult = callStacktraceHandles(
            stacktraceHandles = stacktraceHandles,
            lineNumbers = stacktraceLineNumbers,
            nextStepIndex = 0,
            invokeCoroutineFunction = invokeCoroutineFunction,
            result = JavaUtilImpl.instance.retrieveResultValue(result),
            coroutineSuspend = COROUTINE_SUSPENDED
        )
        if (bottomResult === COROUTINE_SUSPENDED) {
            return
        }
        bottomContinuation.resumeWith(Result.success(bottomResult))
    }

    private fun fillStacktraceArrays(
        baseContinuations: List<BaseContinuationImpl>,
        stacktraceElements: DecoroutinatorContinuationStacktraceElements,
        stacktraceDepth: Int,
        stacktraceHandles: Array<MethodHandle>,
        stacktraceLineNumbers: IntArray
    ) {
        val stacktraceElement2StacktraceMethodHandle = decoroutinatorRegistry.stacktraceMethodHandleRegistry
            .getStacktraceMethodHandles(stacktraceElements.possibleElements)
        (0 until stacktraceDepth).forEach { index ->
            val continuation = baseContinuations[index]
            val element = stacktraceElements.continuation2Element[continuation]
            if (element == null) {
                stacktraceHandles[index] = unknownStacktraceMethodHandle
                stacktraceLineNumbers[index] = -1
            } else {
                stacktraceHandles[index] = stacktraceElement2StacktraceMethodHandle[element]!!
                stacktraceLineNumbers[index] = element.lineNumber
            }
        }
    }

    private fun recoveryExplicitStacktrace(
        exception: Throwable,
        baseContinuations: List<BaseContinuationImpl>,
        stacktraceElements: DecoroutinatorContinuationStacktraceElements
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
        StackTraceElement("\b\b\b($message", "\b", "\b", -1)

    // further code was copied from stdlib coroutine implementation

    private fun stdlibResumeWith(result: Result<Any?>) {
        var current = this
        var param = result
        while (true) {
            JavaUtilImpl.probeCoroutineResumed(current)
            with(current) {
                val completion = completion!!
                val outcome: Result<Any?> =
                    try {
                        val outcome = invokeSuspend(param)
                        if (outcome === COROUTINE_SUSPENDED) return
                        Result.success(outcome)
                    } catch (exception: Throwable) {
                        Result.failure(exception)
                    }
                releaseIntercepted()
                if (completion is BaseContinuationImpl) {
                    current = completion
                    param = outcome
                } else {
                    completion.resumeWith(outcome)
                    return
                }
            }
        }
    }

    protected abstract fun invokeSuspend(result: Result<Any?>): Any?

    protected open fun releaseIntercepted() { }

    open fun create(completion: Continuation<*>): Continuation<Unit> {
        throw UnsupportedOperationException("create(Continuation) has not been overridden")
    }

    open fun create(value: Any?, completion: Continuation<*>): Continuation<Unit> {
        throw UnsupportedOperationException("create(Any?;Continuation) has not been overridden")
    }

    override fun toString(): String =
        "Continuation at ${getStackTraceElement() ?: this::class.java.name}"

    override val callerFrame: CoroutineStackFrame?
        get() = completion as? CoroutineStackFrame

    override fun getStackTraceElement(): StackTraceElement? =
       JavaUtilImpl.getStackTraceElementImpl(this)
}
