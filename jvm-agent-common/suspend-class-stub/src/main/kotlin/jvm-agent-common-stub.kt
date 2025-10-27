@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal

import dev.reformator.bytecodeprocessor.intrinsics.ClassNameConstant
import kotlin.coroutines.suspendCoroutine

@Suppress("unused")
@ClassNameConstant("jvmAgentCommonSuspendClassName")
private class PreloadStub {
    suspend fun suspendFun() {
        suspendCoroutine<Unit> {  }
        suspendCoroutine<Unit> {  }
    }
}
