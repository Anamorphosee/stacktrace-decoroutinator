@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.test.basecontinuationaccessorstub

import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessorProvider
import java.lang.invoke.MethodHandles
import java.lang.reflect.InvocationTargetException

class TestReflectBaseContinuationAccessorProviderAccessor: BaseContinuationAccessorProvider {
    override fun createAccessor(lookup: MethodHandles.Lookup): BaseContinuationAccessor {
        val invokeSuspendMethod = BaseContinuation::class.java.getDeclaredMethod(
            BaseContinuation::invokeSuspend.name,
            Object::class.java
        )
        invokeSuspendMethod.isAccessible = true
        val releaseInterceptedMethod =
            BaseContinuation::class.java.getDeclaredMethod(BaseContinuation::releaseIntercepted.name)
        releaseInterceptedMethod.isAccessible = true
        return object: BaseContinuationAccessor {
            override fun invokeSuspend(baseContinuation: Any, result: Any?): Any? =
                try {
                    invokeSuspendMethod.invoke(baseContinuation, result)
                } catch (e: InvocationTargetException) {
                    e.cause?.let { throw it }
                    throw e
                }

            override fun releaseIntercepted(baseContinuation: Any) {
                releaseInterceptedMethod.invoke(baseContinuation)
            }
        }
    }
}
