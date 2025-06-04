@file:Suppress("PackageDirectoryMismatch")
@file:ChangeClassName(toName = "kotlinx.coroutines.debug.internal.DecoroutinatorDebugProbesProviderUtilsKt")

package dev.reformator.stacktracedecoroutinator.gradleplugin.embeddeddebugprobes

import dev.reformator.bytecodeprocessor.intrinsics.ChangeClassName
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.runtimesettings.DecoroutinatorRuntimeSettingsProvider
import java.util.ServiceConfigurationError
import java.util.ServiceLoader
import kotlin.coroutines.Continuation

@Suppress("unused")
@ChangeClassName(toName = "kotlinx.coroutines.debug.internal.DecoroutinatorDebugProbesProviderImpl")
class DecoroutinatorDebugProbesProviderImpl: DecoroutinatorDebugProbesProvider {
    init {
        val settingsProvider = loadRuntimeSettingsProvider()
        AgentInstallationType.isInstalledStatically = true
        DebugProbesImpl.enableCreationStackTraces = settingsProvider.enableCreationStackTraces
        if (settingsProvider.installDebugProbes) {
            DebugProbesImpl.install()
        }
    }

    override fun <T> probeCoroutineCreated(completion: Continuation<T>): Continuation<T> =
        DebugProbesImpl.probeCoroutineCreated(completion)

    override fun probeCoroutineResumed(frame: Continuation<*>) {
        DebugProbesImpl.probeCoroutineResumed(frame)
    }

    override fun probeCoroutineSuspended(frame: Continuation<*>) {
        DebugProbesImpl.probeCoroutineSuspended(frame)
    }
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

private fun <T: Any> loadServices(type: Class<T>): Pair<List<T>, List<ServiceConfigurationError>> {
    val services = mutableListOf<T>()
    val errors = mutableListOf<ServiceConfigurationError>()
    val iter = ServiceLoader.load(type).iterator()
    while (true) {
        try {
            if (!iter.hasNext()) {
                break
            }
            services.add(iter.next())
        } catch (e: ServiceConfigurationError) {
            errors.add(e)
        }
    }
    return Pair(services, errors)
}

private fun loadRuntimeSettingsProvider(): DecoroutinatorRuntimeSettingsProvider {
    val services = loadServices(DecoroutinatorRuntimeSettingsProvider::class.java)
    if (services.first.isEmpty()) {
        return DecoroutinatorRuntimeSettingsProvider
    }
    val maxPriority = services.first.maxOf { it.priority }
    val providersWithMaxPriority = services.first.filter { it.priority == maxPriority }
    if (providersWithMaxPriority.size > 1) {
        throw IllegalStateException(
            "Multiple DecoroutinatorRuntimeSettingsProvider implementations with max priority found: $providersWithMaxPriority"
        )
    }
    return providersWithMaxPriority.first()
}
