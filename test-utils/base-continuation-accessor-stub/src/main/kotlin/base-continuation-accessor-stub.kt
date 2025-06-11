@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.test.basecontinuationaccessorstub

import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessorProvider
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

class TestBaseContinuationAccessorProviderAccessor: BaseContinuationAccessorProvider {
    override fun createAccessor(lookup: MethodHandles.Lookup): BaseContinuationAccessor {
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
        return object : BaseContinuationAccessor {
            override fun invokeSuspend(baseContinuation: Any, result: Any?): Any? =
                invokeSuspendHandle.invokeExact(baseContinuation as BaseContinuation, result)

            override fun releaseIntercepted(baseContinuation: Any) {
                releaseInterceptedHandle.invokeExact(baseContinuation as BaseContinuation)
            }
        }
    }
}
