@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.runtime.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.internal.DecoroutinatorProvider
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

internal class Provider: DecoroutinatorProvider {
    override fun registerTransformedClass(lookup: MethodHandles.Lookup) {
        TransformedClassesRegistry.registerTransformedClass(lookup)
    }

    override fun isPrepared() =
        invokeSuspendHandle != null && releaseInterceptedHandle != null

    override fun isEnabled() =
        enabled

    override fun prepare(lookup: MethodHandles.Lookup) {
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

    override fun awake(baseContinuation: Any, result: Any?) {
        (baseContinuation as BaseContinuation).awake(result)
    }
}
