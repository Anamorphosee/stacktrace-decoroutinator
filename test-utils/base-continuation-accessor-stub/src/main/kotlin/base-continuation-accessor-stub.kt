@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.test.basecontinuationaccessorstub

import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorBaseContinuationAccessor
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorBaseContinuationAccessorProvider
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

class TestBaseContinuationAccessorProviderAccessor: DecoroutinatorBaseContinuationAccessorProvider {
    override fun createAccessor(lookup: MethodHandles.Lookup): DecoroutinatorBaseContinuationAccessor {
        val invokeSuspendHandle = lookup.findVirtual(
            BaseContinuation::class.java,
            BaseContinuation::invokeSuspend.name,
            MethodType.methodType(Object::class.java, Object::class.java)
        )
        val releaseInterceptedHandle = lookup.findVirtual(
            BaseContinuation::class.java,
            BaseContinuation::releaseIntercepted.name,
            MethodType.methodType(Void::class.javaPrimitiveType)
        )
        return object: DecoroutinatorBaseContinuationAccessor {
            override fun invokeSuspend(baseContinuation: Any, result: Any?): Any? =
                invokeSuspendHandle.invokeExact(baseContinuation as BaseContinuation, result)

            override fun releaseIntercepted(baseContinuation: Any) {
                releaseInterceptedHandle.invokeExact(baseContinuation as BaseContinuation)
            }
        }
    }
}