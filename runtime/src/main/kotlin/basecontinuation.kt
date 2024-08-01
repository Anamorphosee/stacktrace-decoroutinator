@file:Suppress("PackageDirectoryMismatch")

package kotlin.coroutines.jvm.internal

import dev.reformator.stacktracedecoroutinator.runtime.DecoroutinatorMarker
import dev.reformator.stacktracedecoroutinator.runtime.decoroutinatorResumeWith
import java.io.Serializable
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

// further code was copied from stdlib coroutine implementation
@DecoroutinatorMarker
internal abstract class BaseContinuationImpl(
    val completion: Continuation<Any?>?
): Continuation<Any?>, CoroutineStackFrame, Serializable {
    final override fun resumeWith(result: Result<Any?>) {
        val standart = try {
            decoroutinatorResumeWith(result)
            false
        } catch (_: NoClassDefFoundError) {
            true
        }
        if (standart) {
            var current = this
            var param = result
            while (true) {
                //JavaUtils().probeCoroutineResumed(current)
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
       null
}
