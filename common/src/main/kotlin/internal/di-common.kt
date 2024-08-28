package dev.reformator.stacktracedecoroutinator.common.internal

import java.lang.invoke.MethodHandles
import java.util.ServiceLoader

internal val supportsVarHandles =
    try {
        _supportsVarHandleStub().check()
        true
    } catch (_: Throwable) {
        false
    }

internal val settingsProvider = ServiceLoader.load(CommonSettingsProvider::class.java).firstOrNull() ?:
    object: CommonSettingsProvider {}

internal val enabled = settingsProvider.decoroutinatorEnabled
internal val recoveryExplicitStacktrace = settingsProvider.recoveryExplicitStacktrace

internal var cookie: Cookie? = null

internal val stacktraceElementsFactory: StacktraceElementsFactory = StacktraceElementsFactoryImpl

internal val specMethodsRegistry: SpecMethodsRegistry =
    ServiceLoader.load(SpecMethodsRegistry::class.java).firstOrNull() ?: SpecMethodsRegistryImpl

@Suppress("ClassName")
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