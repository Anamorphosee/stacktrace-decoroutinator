package dev.reformator.stacktracedecoroutinator.common.internal

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.util.ServiceLoader

internal const val ENABLED_PROPERTY = "dev.reformator.stacktracedecoroutinator.enabled"

internal val settingsProvider = ServiceLoader.load(CommonSettingsProvider::class.java).firstOrNull() ?:
    object: CommonSettingsProvider {}

internal val enabled = settingsProvider.decoroutinatorEnabled
internal val recoveryExplicitStacktrace = settingsProvider.recoveryExplicitStacktrace

internal var invokeSuspendHandle: MethodHandle? = null
internal var releaseInterceptedHandle: MethodHandle? = null

internal val stacktraceElementsFactory: StacktraceElementsFactory = StacktraceElementsFactoryImpl

internal val specMethodsRegistry: SpecMethodsRegistry =
    ServiceLoader.load(SpecMethodsRegistry::class.java).firstOrNull() ?: SpecMethodsRegistryImpl

internal val supportsVarHandles = run {
    @Suppress("ClassName")
    class _stub {
        @Suppress("unused")
        @JvmField var field: Int = 0
    }
    try {
        MethodHandles.lookup().findVarHandle(_stub::class.java, "field", Int::class.javaPrimitiveType)
        true
    } catch (_: Throwable) { false }
}
