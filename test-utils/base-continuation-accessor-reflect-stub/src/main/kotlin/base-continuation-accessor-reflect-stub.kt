@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.test.basecontinuationaccessorstub

import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorBaseContinuationAccessor
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorBaseContinuationAccessorProvider
import java.lang.invoke.MethodHandles
import java.lang.reflect.InvocationTargetException

class TestReflectBaseContinuationAccessorProviderAccessor: DecoroutinatorBaseContinuationAccessorProvider {
    override fun createAccessor(lookup: MethodHandles.Lookup): DecoroutinatorBaseContinuationAccessor {
        val invokeSuspendMethod = BaseContinuation::class.java.getDeclaredMethod(
            BaseContinuation::invokeSuspend.name,
            Object::class.java
        )
        invokeSuspendMethod.isAccessible = true
        val releaseInterceptedMethod =
            BaseContinuation::class.java.getDeclaredMethod(BaseContinuation::releaseIntercepted.name)
        releaseInterceptedMethod.isAccessible = true
        return object: DecoroutinatorBaseContinuationAccessor {
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