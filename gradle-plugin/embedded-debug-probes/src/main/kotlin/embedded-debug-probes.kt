@file:Suppress("PackageDirectoryMismatch")
@file:ChangeClassName(toName = "kotlin.coroutines.jvm.internal.DebugProbesKt")

package dev.reformator.stacktracedecoroutinator.gradleplugin.embeddeddebugprobes

import dev.reformator.bytecodeprocessor.intrinsics.AddToStaticInitializer
import dev.reformator.bytecodeprocessor.intrinsics.ChangeClassName
import dev.reformator.bytecodeprocessor.intrinsics.fail
import kotlin.coroutines.Continuation

@Suppress("unused")
@AddToStaticInitializer
private fun clinit() {
    AgentInstallationType.isInstalledStatically = true
    DebugProbesImpl.enableCreationStackTraces =
        System.getProperty("dev.reformator.stacktracedecoroutinator.enableCreationStackTraces", "false").toBoolean()
    val installDebugProbes =
        System.getProperty("dev.reformator.stacktracedecoroutinator.installDebugProbes", "true").toBoolean()
    if (installDebugProbes) {
        DebugProbesImpl.install()
    }
}

@Suppress("unused")
fun <T> probeCoroutineCreated(completion: Continuation<T>): Continuation<T> =
    DebugProbesImpl.probeCoroutineCreated(completion)

@Suppress("unused")
fun probeCoroutineResumed(frame: Continuation<*>) {
    DebugProbesImpl.probeCoroutineResumed(frame)
}

@Suppress("unused")
fun probeCoroutineSuspended(frame: Continuation<*>) {
    DebugProbesImpl.probeCoroutineSuspended(frame)
}

@Suppress("UNUSED_PARAMETER")
@ChangeClassName(toName = "kotlinx.coroutines.debug.internal.DebugProbesImpl", deleteAfterChanging = true)
private object DebugProbesImpl {
    var enableCreationStackTraces: Boolean
        @JvmName("getEnableCreationStackTraces\$kotlinx_coroutines_core")
        get() = fail()
        @JvmName("setEnableCreationStackTraces\$kotlinx_coroutines_core")
        set(value) { fail() }

    @JvmName("install\$kotlinx_coroutines_core")
    fun install() { fail() }

    @JvmName("probeCoroutineCreated\$kotlinx_coroutines_core")
    fun <T> probeCoroutineCreated(completion: Continuation<T>): Continuation<T> { fail() }

    @JvmName("probeCoroutineResumed\$kotlinx_coroutines_core")
    fun probeCoroutineResumed(frame: Continuation<*>) { fail() }

    @JvmName("probeCoroutineSuspended\$kotlinx_coroutines_core")
    fun probeCoroutineSuspended(frame: Continuation<*>) { fail() }
}

@ChangeClassName(toName = "kotlinx.coroutines.debug.internal.AgentInstallationType", deleteAfterChanging = true)
@Suppress("UNUSED_PARAMETER")
private object AgentInstallationType {
    var isInstalledStatically: Boolean
        @JvmName("isInstalledStatically\$kotlinx_coroutines_core")
        get() = fail()
        @JvmName("setInstalledStatically\$kotlinx_coroutines_core")
        set(value) { fail() }
}
