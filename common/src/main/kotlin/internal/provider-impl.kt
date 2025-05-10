@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.internal.DecoroutinatorProvider
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.Continuation

internal class Provider: DecoroutinatorProvider {
    override val isDecoroutinatorEnabled: Boolean
        get() = enabled

    override val cookie: Any?
        get() = dev.reformator.stacktracedecoroutinator.common.internal.cookie

    override fun prepareCookie(lookup: MethodHandles.Lookup): Any {
        prepareCookieLock.withLock {
            dev.reformator.stacktracedecoroutinator.common.internal.cookie?.let { return it }
            val invokeSuspendHandle = lookup.findVirtual(
                BaseContinuation::class.java,
                "invokeSuspend",
                MethodType.methodType(Any::class.java, Any::class.java)
            )
            val releaseInterceptedHandle = lookup.findVirtual(
                BaseContinuation::class.java,
                "releaseIntercepted",
                MethodType.methodType(Void::class.javaPrimitiveType)
            )
            val cookie = Cookie(
                invokeSuspendHandle = invokeSuspendHandle,
                releaseInterceptedHandle = releaseInterceptedHandle
            )
            dev.reformator.stacktracedecoroutinator.common.internal.cookie = cookie
            return cookie
        }
    }

    override fun awakeBaseContinuation(cookie: Any, baseContinuation: Any, result: Any?) {
        (baseContinuation as BaseContinuation).awake(cookie as Cookie, result)
    }

    override fun registerTransformedClass(lookup: MethodHandles.Lookup) {
        TransformedClassesRegistry.registerTransformedClass(lookup)
    }

    @Suppress("UNCHECKED_CAST")
    override fun getBaseContinuation(
        completion: Any?,
        fileName: String?,
        className: String,
        methodName: String,
        lineNumber: Int
    ): Any? {
        if (!tailCallDeoptimize || completion == null) {
            return completion
        }
        val isCompletionBaseContinuation = completion is BaseContinuation
        if (isCompletionBaseContinuation && completion !is DecoroutinatorContinuationImpl) {
            completion as BaseContinuation
            val label = stacktraceElementsFactory.getLabelExtractor(completion).getLabel(completion)
            if (label == UNKNOWN_LABEL || label and Int.MIN_VALUE != 0) {
                return completion
            }
        }
        return DecoroutinatorContinuationImpl(
            completion = completion as Continuation<Any?>,
            fileName = fileName,
            className = className,
            methodName = methodName,
            lineNumber = lineNumber
        )
    }

    private val prepareCookieLock = ReentrantLock()
}
