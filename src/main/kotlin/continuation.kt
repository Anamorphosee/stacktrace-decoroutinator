package kotlin.coroutines.jvm.internal

import dev.reformator.stacktracedecoroutinator.analyzer.DecoroutinatorClassAnalyzer
import dev.reformator.stacktracedecoroutinator.analyzer.DecoroutinatorClassAnalyzerImpl
import dev.reformator.stacktracedecoroutinator.generator.DecoroutinatorClassLoader
import dev.reformator.stacktracedecoroutinator.util.callStack
import dev.reformator.stacktracedecoroutinator.util.getStackTraceElementHandle
import dev.reformator.stacktracedecoroutinator.util.probeCoroutineResumedHandle
import java.io.Serializable
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiFunction
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

data class DecoroutinatorContinuationSpec(
    val handle: MethodHandle,
    val labelField: Field,
    val label2LineNumber: Map<Int, Int>
)

internal abstract class BaseContinuationImpl(
    val completion: Continuation<Any?>
): Continuation<Any?>, CoroutineStackFrame, Serializable {
    companion object {
        var analyzer: DecoroutinatorClassAnalyzer = DecoroutinatorClassAnalyzerImpl()
        var classLoader: DecoroutinatorClassLoader = DecoroutinatorClassLoader()

        private val continuationClass2Spec = ConcurrentHashMap<Class<*>, DecoroutinatorContinuationSpec>()
    }

    final override fun resumeWith(result: Result<Any?>) {
        var lastContinuation: Continuation<Any?> = this

        val baseContinuations = buildList {
            while (true) {
                val lastContinuationVal = lastContinuation
                if (lastContinuationVal !is BaseContinuationImpl) {
                    break
                }
                add(lastContinuationVal)
                lastContinuation = lastContinuationVal.completion
            }
            reverse()
        }

        val lineNumbers = IntArray(baseContinuations.lastIndex)
        val handles = fillStacktraceArrays(baseContinuations, lineNumbers, baseContinuations.lastIndex)
        val invokeHandleFunction = BiFunction { index: Int, result: Any? ->
            val continuation = baseContinuations[index]
            probeCoroutineResumedHandle.invokeExact(continuation)
            val outcome = try {
                continuation.invokeSuspend(Result.success(result))
            } catch (e: Throwable) {
                Result.failure<Any?>(e)
            }
            if (outcome !== COROUTINE_SUSPENDED) {
                continuation.releaseIntercepted()
            }
            outcome
        }
        val lastContinuationResult = callStack(handles, lineNumbers, invokeHandleFunction, result)

        if (lastContinuationResult !== COROUTINE_SUSPENDED) {
            lastContinuation.resumeWith(Result.success(lastContinuation))
        }
    }

    private fun fillStacktraceArrays(
        baseContinuations: List<BaseContinuationImpl>,
        lineNumbers: IntArray,
        size: Int
    ): Array<MethodHandle> {
        val continuationClass2Spec = baseContinuations.asSequence()
            .take(size)
            .map { it.javaClass }
            .distinct()
            .map { it to getContinuationSpec(it) }
            .toMap()
        return Array(size) { index ->
            val continuation = baseContinuations[index]
            val spec = continuationClass2Spec[continuation.javaClass]!!
            val label = spec.labelField[continuation] as Int
            lineNumbers[index] = spec.label2LineNumber[label]!!
            spec.handle
        }
    }

    private fun getContinuationSpec(continuationClazz: Class<*>) =
        continuationClass2Spec[continuationClazz] ?: run {
            val continuationClassName = continuationClazz.canonicalName
            val className = analyzer.getClassNameByContinuationClassName(continuationClassName)
            val analyzerSpec = analyzer.getDecoroutinatorClassSpec(className)
            val methodName2StacktraceHandle: Map<String, MethodHandle> =
                classLoader.getMethodName2StacktraceHandlerMap(className, analyzerSpec)
            analyzerSpec.continuationClassName2Method.forEach { (continuationClassName, methodSpec) ->
                val continuationClass = Class.forName(continuationClassName)
                val stacktraceHandle = methodName2StacktraceHandle[methodSpec.methodName]!!
                val labelField = continuationClass.getDeclaredField("label")
                labelField.isAccessible = true
                continuationClass2Spec[continuationClass] = DecoroutinatorContinuationSpec(
                    handle = stacktraceHandle,
                    labelField = labelField,
                    label2LineNumber = methodSpec.label2LineNumber
                )
            }
            continuationClass2Spec[continuationClazz]!!
        }

    // further code was copied from JetBrains coroutine implementation

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
        getStackTraceElementHandle.invokeExact(this) as StackTraceElement?
}
