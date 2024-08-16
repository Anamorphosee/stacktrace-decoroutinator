@file:Suppress("PackageDirectoryMismatch")

package kotlin.coroutines.jvm.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.*
import dev.reformator.stacktracedecoroutinator.provider.stdlib.*
import java.io.Serializable
import java.lang.invoke.MethodHandles
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

// further code was copied from stdlib coroutine implementation
@DecoroutinatorMarker
internal abstract class BaseContinuationImpl(
    val completion: Continuation<Any?>?
): Continuation<Any?>, CoroutineStackFrame, Serializable {
    final override fun resumeWith(result: Result<Any?>) {
        if (isEnabled) {
            if (!isPrepared) {
                prepare(MethodHandles.lookup())
            }
            awake(this, result.value)
        } else {
            var current = this
            var param = result
            while (true) {
                probeCoroutineResumed(current)
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

    @Suppress("unused")
    open fun create(completion: Continuation<*>): Continuation<Unit> {
        throw UnsupportedOperationException("create(Continuation) has not been overridden")
    }

    @Suppress("unused")
    open fun create(value: Any?, completion: Continuation<*>): Continuation<Unit> {
        throw UnsupportedOperationException("create(Any?;Continuation) has not been overridden")
    }

    override fun toString(): String =
        "Continuation at ${getStackTraceElement() ?: this::class.java.name}"

    override val callerFrame: CoroutineStackFrame?
        get() = completion as? CoroutineStackFrame

    override fun getStackTraceElement(): StackTraceElement? =
        toBaseContinuation.getStackTraceElementImpl()
}
