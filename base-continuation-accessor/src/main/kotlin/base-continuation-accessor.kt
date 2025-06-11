@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.basecontinuationaccessor

import dev.reformator.bytecodeprocessor.intrinsics.ChangeClassName
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessorProvider
import java.lang.invoke.MethodHandles

@Suppress("unused")
@ChangeClassName(toName = "kotlin.coroutines.jvm.internal.DecoroutinatorBaseContinuationAccessorImpl")
class DecoroutinatorBaseContinuationAccessorImpl: BaseContinuationAccessor, BaseContinuationAccessorProvider {
    override fun invokeSuspend(baseContinuation: Any, result: Any?): Any? =
        (baseContinuation as BaseContinuation).invokeSuspend(result)

    override fun releaseIntercepted(baseContinuation: Any) {
        (baseContinuation as BaseContinuation).releaseIntercepted()
    }

    override fun createAccessor(lookup: MethodHandles.Lookup): BaseContinuationAccessor =
        this
}
