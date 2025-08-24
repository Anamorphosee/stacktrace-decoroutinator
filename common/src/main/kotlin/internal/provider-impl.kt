@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor
import dev.reformator.stacktracedecoroutinator.provider.internal.DecoroutinatorProvider
import java.lang.invoke.MethodHandles
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.Continuation

internal class Provider: DecoroutinatorProvider {
    private val prepareBaseContinuationAccessorLock = ReentrantLock()

    override val isDecoroutinatorEnabled: Boolean
        get() = enabled

    override val baseContinuationAccessor: BaseContinuationAccessor?
        get() = dev.reformator.stacktracedecoroutinator.common.internal.baseContinuationAccessor

    @Suppress("NewApi")
    override fun prepareBaseContinuationAccessor(lookup: MethodHandles.Lookup): BaseContinuationAccessor =
        prepareBaseContinuationAccessorLock.withLock {
            baseContinuationAccessor?.let { return it }
            val accessor = baseContinuationAccessorProvider.createAccessor(lookup)
            dev.reformator.stacktracedecoroutinator.common.internal.baseContinuationAccessor = accessor
            accessor
        }

    override fun awakeBaseContinuation(
        accessor: BaseContinuationAccessor,
        baseContinuation: Any,
        result: Any?
    ) {
        (baseContinuation as BaseContinuation).awake(accessor, result)
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
        if (completion is BaseContinuation && completion !is DecoroutinatorContinuationImpl) {
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

    override val isUsingElementFactoryForBaseContinuationEnabled: Boolean
        get() = dev.reformator.stacktracedecoroutinator.common.internal.isUsingElementFactoryForBaseContinuationEnabled

    override fun getElementFactoryStacktraceElement(baseContinuation: Any): StackTraceElement? =
        stacktraceElementsFactory.getStacktraceElement(baseContinuation as BaseContinuation)
}
