package kotlin.coroutines.jvm.internal

import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorMarker
import dev.reformator.stacktracedecoroutinator.common.decoroutinatorRegistry
import dev.reformator.stacktracedecoroutinator.common.JavaUtilsImpl
import dev.reformator.stacktracedecoroutinator.stdlib.decoroutinatorResumeWith
import dev.reformator.stacktracedecoroutinator.stdlib.invokeSuspendFunc
import java.io.Serializable
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

// further code was copied from stdlib coroutine implementation
@DecoroutinatorMarker
internal abstract class BaseContinuationImpl(
    val completion: Continuation<Any?>?
): Continuation<Any?>, CoroutineStackFrame, Serializable {
    final override fun resumeWith(result: Result<Any?>) {
        if (decoroutinatorRegistry.enabled) {
            val invokeSuspendFuncLocal: MethodHandle = invokeSuspendFunc ?: run {
                val result = MethodHandles.lookup().findVirtual(BaseContinuationImpl::class.java, "invokeSuspend", MethodType.methodType(Object::class.java, Object::class.java))
                invokeSuspendFunc = result
                result
            }
            decoroutinatorResumeWith(
                result,
                invokeSuspendFunc = invokeSuspendFuncLocal,
                releaseInterceptedFunc = { releaseIntercepted() }
            )
        } else {
            var current = this
            var param = result
            while (true) {
                JavaUtilsImpl.probeCoroutineResumed(current)
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
       JavaUtilsImpl.getStackTraceElementImpl(this)
}
