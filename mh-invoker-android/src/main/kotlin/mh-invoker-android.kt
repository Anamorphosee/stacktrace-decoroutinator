@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("MhInvokerAndroidKt")

package dev.reformator.stacktracedecoroutinator.mhinvokerandroid

import android.util.Base64
import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.common.internal.MethodHandleInvoker
import dalvik.system.InMemoryDexClassLoader
import java.nio.ByteBuffer

internal class AndroidMethodHandleInvoker: MethodHandleInvoker by loadRegularMethodHandleInvoker()

private val regularMethodHandleDexBase64: String
    @LoadConstant get() { fail() }

private val String.decodeBase64: ByteArray
    get() = Base64.decode(this, Base64.DEFAULT)

private fun loadRegularMethodHandleInvoker(): MethodHandleInvoker =
    InMemoryDexClassLoader(
        ByteBuffer.wrap(regularMethodHandleDexBase64.decodeBase64),
        MethodHandleInvoker::class.java.classLoader
    ).loadClass("dev.reformator.stacktracedecoroutinator.mhinvoker.internal.RegularMethodHandleInvoker")
        .getDeclaredConstructor()
        .newInstance() as MethodHandleInvoker
