@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.internal.DecoroutinatorProvider
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

internal class Provider: DecoroutinatorProvider {
    override val isDecoroutinatorEnabled: Boolean
        get() = enabled

    override val isBaseContinuationPrepared: Boolean
        get() = invokeSuspendHandle != null && releaseInterceptedHandle != null

    override fun prepareBaseContinuation(lookup: MethodHandles.Lookup) {
        invokeSuspendHandle = lookup.findVirtual(
            BaseContinuation::class.java,
            "invokeSuspend",
            MethodType.methodType(Any::class.java, Any::class.java)
        )
        releaseInterceptedHandle = lookup.findVirtual(
            BaseContinuation::class.java,
            "releaseIntercepted",
            MethodType.methodType(Void::class.javaPrimitiveType)
        )
    }

    override fun awakeBaseContinuation(baseContinuation: Any, result: Any?) {
        (baseContinuation as BaseContinuation).awake(result)
    }

    override fun registerTransformedClass(lookup: MethodHandles.Lookup) {
        TransformedClassesRegistry.registerTransformedClass(lookup)
    }
}
