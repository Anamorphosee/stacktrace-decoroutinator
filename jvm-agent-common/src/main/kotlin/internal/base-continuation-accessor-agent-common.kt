@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal

import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorBaseContinuationAccessor
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorBaseContinuationAccessorProvider
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.Base64

internal class AgentBaseContinuationAccessorProvider: DecoroutinatorBaseContinuationAccessorProvider {
    override fun createAccessor(lookup: MethodHandles.Lookup): DecoroutinatorBaseContinuationAccessor {
        try {
            return loadRegularAccessor(lookup)
        } catch (_: Throwable) { }

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

private fun loadRegularAccessor(lookup: MethodHandles.Lookup): DecoroutinatorBaseContinuationAccessor {
    val regularProviderClass: Class<*> = lookup.defineClass(Base64.getDecoder().decode(regularAccessorBodyBase64))
    return regularProviderClass.getDeclaredConstructor().newInstance() as DecoroutinatorBaseContinuationAccessor
}

private val regularAccessorBodyBase64: String
    @LoadConstant get() { fail() }
