@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal

import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessorProvider
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.Base64
import java.util.zip.ZipInputStream

internal class AgentBaseContinuationAccessorProvider: BaseContinuationAccessorProvider {
    override fun createAccessor(lookup: MethodHandles.Lookup): BaseContinuationAccessor {
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
        return object: BaseContinuationAccessor {
            override fun invokeSuspend(baseContinuation: Any, result: Any?): Any? =
                invokeSuspendHandle.invokeExact(baseContinuation as BaseContinuation, result)

            override fun releaseIntercepted(baseContinuation: Any) {
                releaseInterceptedHandle.invokeExact(baseContinuation as BaseContinuation)
            }
        }
    }
}

private fun loadRegularAccessor(lookup: MethodHandles.Lookup): BaseContinuationAccessor {
    var baseContinuationAccessorClass: Class<*>? = null
    ZipInputStream(Base64.getDecoder().decode(baseContinuationAccessorJarBase64).inputStream()).use { input ->
        while (true) {
            val entry = input.nextEntry ?: break
            if (entry.name.endsWith(".class")) {
                val body = input.readBytes()
                lookup.defineClass(body).let { definedClass ->
                    if (definedClass.name == baseContinuationAccessorImplClassName) {
                        baseContinuationAccessorClass = definedClass
                    }
                }
            }
        }
    }
    return baseContinuationAccessorClass!!.getDeclaredConstructor().newInstance() as BaseContinuationAccessor
}

private val baseContinuationAccessorJarBase64: String
    @LoadConstant("baseContinuationAccessorJarBase64") get() { fail() }

private val baseContinuationAccessorImplClassName: String
    @LoadConstant("baseContinuationAccessorImplClassName") get() { fail() }
