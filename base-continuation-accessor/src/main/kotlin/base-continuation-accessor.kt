@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.basecontinuationaccessor

import dev.reformator.bytecodeprocessor.intrinsics.ChangeClassName
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorBaseContinuationAccessor
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorBaseContinuationAccessorProvider
import java.lang.invoke.MethodHandles

@Suppress("unused")
@ChangeClassName(toName = "kotlin.coroutines.jvm.internal.DecoroutinatorBaseContinuationAccessorImpl")
class DecoroutinatorBaseContinuationAccessorImpl: DecoroutinatorBaseContinuationAccessor, DecoroutinatorBaseContinuationAccessorProvider {
    override fun invokeSuspend(baseContinuation: Any, result: Any?): Any? =
        (baseContinuation as BaseContinuation).invokeSuspend(result)

    override fun releaseIntercepted(baseContinuation: Any) {
        (baseContinuation as BaseContinuation).releaseIntercepted()
    }

    override fun createAccessor(lookup: MethodHandles.Lookup): DecoroutinatorBaseContinuationAccessor =
        this
}
