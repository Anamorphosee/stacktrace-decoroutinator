package dev.reformator.stacktracedecoroutinator.common.internal

import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

internal val supportsMethodHandle =
    try {
        _supportsMethodHandleStub().check()
        true
    } catch (_: Throwable) { false }
internal val settingsProvider =
    if (supportsMethodHandle) {
        loadService<CommonSettingsProvider>()
    } else {
        null
    } ?: CommonSettingsProvider
internal val enabled = supportsMethodHandle && settingsProvider.decoroutinatorEnabled
internal val recoveryExplicitStacktrace = enabled && settingsProvider.recoveryExplicitStacktrace
internal val tailCallDeoptimize = enabled && settingsProvider.tailCallDeoptimize
internal val recoveryExplicitStacktraceTimeoutMs =
    if (tailCallDeoptimize) settingsProvider.recoveryExplicitStacktraceTimeoutMs else 0U
internal val methodsNumberThreshold = if (enabled) settingsProvider.methodsNumberThreshold else 0

internal var cookie: Cookie? = null

@Suppress("ObjectPropertyName")
private val _methodHandleInvoker: MethodHandleInvoker? =
    if (enabled) {
        loadMandatoryService<MethodHandleInvoker>()
    } else {
        null
    }

@Suppress("ObjectPropertyName")
private val _stacktraceElementsFactory: StacktraceElementsFactory? =
    if (enabled) StacktraceElementsFactoryImpl() else null

@Suppress("ObjectPropertyName")
private val _specMethodsRegistry: SpecMethodsRegistry? =
    if (enabled) {
        loadService<SpecMethodsRegistry>() ?: SpecMethodsRegistryImpl
    } else {
        null
    }

internal val annotationMetadataResolver: AnnotationMetadataResolver? =
    if (enabled) {
        loadService<AnnotationMetadataResolver>()
    } else {
        null
    }

@Suppress("ObjectPropertyName")
private val _varHandleInvoker: VarHandleInvoker? =
    if (_methodHandleInvoker?.supportsVarHandle == true) {
        loadMandatoryService<VarHandleInvoker>()
    } else {
        null
    }

val methodHandleInvoker: MethodHandleInvoker
    get() = _methodHandleInvoker!!

internal val stacktraceElementsFactory: StacktraceElementsFactory
    get() = _stacktraceElementsFactory!!

internal val specMethodsRegistry: SpecMethodsRegistry
    get() = _specMethodsRegistry!!

internal val varHandleInvoker: VarHandleInvoker
    get() = _varHandleInvoker!!

@Suppress("ClassName")
private class _supportsMethodHandleStub {
    @Suppress("NewApi")
    fun check() {
        val methodHandle = MethodHandles.lookup().findVirtual(
            _supportsMethodHandleStub::class.java,
            ::met.name,
            MethodType.methodType(Void.TYPE)
        )
        assert { methodHandle != null }
    }
    fun met() { }
}
