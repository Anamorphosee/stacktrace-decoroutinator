@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.internal.DecoroutinatorProvider
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

internal class Provider: DecoroutinatorProvider {
    override val isDecoroutinatorEnabled: Boolean
        get() = enabled

    override val cookie: Any?
        get() = dev.reformator.stacktracedecoroutinator.common.internal.cookie

    override fun prepareCookie(lookup: MethodHandles.Lookup): Any {
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

    override fun awakeBaseContinuation(cookie: Any, baseContinuation: Any, result: Any?) {
        (baseContinuation as BaseContinuation).awake(cookie as Cookie, result)
    }

    override fun registerTransformedClass(lookup: MethodHandles.Lookup) {
        TransformedClassesRegistry.registerTransformedClass(lookup)
    }
}
