@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.mhinvoker.internal

import dev.reformator.stacktracedecoroutinator.common.internal.MethodHandleInvoker
import dev.reformator.stacktracedecoroutinator.common.internal.VarHandleInvoker
import dev.reformator.stacktracedecoroutinator.common.internal.assert
import dev.reformator.stacktracedecoroutinator.common.internal.specMethodType
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorApi
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle

internal class RegularMethodHandleInvoker: MethodHandleInvoker {
    override val unknownSpecMethodHandle: MethodHandle =
        MethodHandles.lookup().findStatic(
            unknownSpecClass,
            ::unknown.name,
            specMethodType
        )

    override fun callSpecMethod(handle: MethodHandle, spec: DecoroutinatorSpec, result: Any?): Any? =
        handle.invokeExact(spec, result)

    override val unknownSpecMethodClass: Class<*> = unknownSpecClass

    override val supportsVarHandle: Boolean =
        try {
            _supportsVarHandleStub().check()
            true
        } catch (_: Throwable) {
            false
        }
}

internal class RegularVarHandleInvoker: VarHandleInvoker {
    override fun getIntVar(
        handle: VarHandle,
        owner: BaseContinuation
    ): Int =
        handle[owner] as Int
}

@Suppress("ClassName")
@DecoroutinatorApi
private class _supportsVarHandleStub {
    private var field: Int = 0
    fun check() {
        val varHandle = MethodHandles.lookup().findVarHandle(
            _supportsVarHandleStub::class.java,
            ::field.name,
            Int::class.javaPrimitiveType
        )
        val fieldValue = varHandle[this] as Int
        assert { fieldValue == 0 }
    }
}
