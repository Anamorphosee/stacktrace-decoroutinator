package kotlin.coroutines.jvm.internal

import dev.reformator.stacktracedecoroutinator.analyzer.DecoroutinatorClassAnalyzer
import dev.reformator.stacktracedecoroutinator.analyzer.DecoroutinatorClassAnalyzerImpl
import dev.reformator.stacktracedecoroutinator.continuation.DecoroutinatorContinuationSpec
import dev.reformator.stacktracedecoroutinator.continuation.DecoroutinatorRuntime
import dev.reformator.stacktracedecoroutinator.generator.DecoroutinatorClassLoader
import dev.reformator.stacktracedecoroutinator.util.JavaUtilImpl
import dev.reformator.stacktracedecoroutinator.util.callStack
import dev.reformator.stacktracedecoroutinator.util.classLoader
import java.io.Serializable
import java.lang.invoke.MethodHandle
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiFunction
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import dev.reformator.stacktracedecoroutinator.util.value

@DecoroutinatorRuntime
internal abstract class BaseContinuationImpl(
    val completion: Continuation<Any?>?
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
                lastContinuation = lastContinuationVal.completion!!
            }
            reverse()
        }

        val lineNumbers = IntArray(baseContinuations.lastIndex)
        val handles = fillStacktraceArrays(baseContinuations, lineNumbers, baseContinuations.lastIndex)
        val invokeHandleFunction = BiFunction { index: Int, result: Any? ->
            val continuation = baseContinuations[index]
            dev.reformator.stacktracedecoroutinator.util.probeCoroutineResumed(continuation)
            val nextResult = try {
                val nextResult = continuation.invokeSuspend(Result.success(result))
                if (nextResult === COROUTINE_SUSPENDED) {
                    return@BiFunction COROUTINE_SUSPENDED
                }
                Result.success(nextResult)
            } catch (e: Throwable) {
                Result.failure(e)
            }
            continuation.releaseIntercepted()
            nextResult.value
        }
        val lastContinuationResult = callStack(handles, lineNumbers, invokeHandleFunction, result.value)

        if (lastContinuationResult !== COROUTINE_SUSPENDED) {
            lastContinuation.resumeWith(Result.success(lastContinuationResult))
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
                BaseContinuationImpl.classLoader.getMethodName2StacktraceHandlerMap(className, analyzerSpec)
            analyzerSpec.continuationClassName2Method.forEach { (continuationClassName, methodSpec) ->
                val continuationClass = this.classLoader!!.loadClass(continuationClassName)
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
        JavaUtilImpl.getStackTraceElementImpl(this)
}
