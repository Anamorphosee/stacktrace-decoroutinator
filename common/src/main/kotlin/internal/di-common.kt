package dev.reformator.stacktracedecoroutinator.common.internal

import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.ServiceLoader

internal val settingsProvider = ServiceLoader.load(CommonSettingsProvider::class.java).firstOrNull() ?:
    object: CommonSettingsProvider {}

internal val supportsMethodHandle = try {
    _supportsMethodHandleStub().check()
    true
} catch (_: Throwable) {
    false
}

internal val enabled = supportsMethodHandle && settingsProvider.decoroutinatorEnabled
internal val recoveryExplicitStacktrace = enabled && settingsProvider.recoveryExplicitStacktrace
internal val tailCallDeoptimize = enabled && settingsProvider.tailCallDeoptimize

internal var cookie: Cookie? = null

@Suppress("ObjectPropertyName")
private val _methodHandleInvoker: MethodHandleInvoker? =
    if (enabled) {
        ServiceLoader.load(MethodHandleInvoker::class.java).iterator().next()
    } else {
        null
    }

@Suppress("ObjectPropertyName")
private val _stacktraceElementsFactory: StacktraceElementsFactory? =
    if (enabled) StacktraceElementsFactoryImpl() else null

@Suppress("ObjectPropertyName")
private val _specMethodsRegistry: SpecMethodsRegistry? =
    if (enabled) {
        ServiceLoader.load(SpecMethodsRegistry::class.java).firstOrNull() ?: SpecMethodsRegistryImpl
    } else {
        null
    }

internal val annotationMetadataResolver: AnnotationMetadataResolver? =
    if (enabled) {
        ServiceLoader.load(AnnotationMetadataResolver::class.java).firstOrNull()
    } else {
        null
    }

@Suppress("ObjectPropertyName")
private val _varHandleInvoker: VarHandleInvoker? =
    if (_methodHandleInvoker?.supportsVarHandle == true) {
        ServiceLoader.load(VarHandleInvoker::class.java).iterator().next()
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
