package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessorProvider
import dev.reformator.stacktracedecoroutinator.provider.internal.AndroidKeep
import dev.reformator.stacktracedecoroutinator.runtimesettings.DecoroutinatorRuntimeSettingsProvider
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

internal val supportsMethodHandle = supportsMethodHandle()

@Suppress("ObjectPropertyName")
private val _settingsProvider = if (supportsMethodHandle) loadRuntimeSettingsProvider() else null

internal val enabled = supportsMethodHandle && _settingsProvider!!.enabled

internal val recoveryExplicitStacktrace = enabled && _settingsProvider!!.recoveryExplicitStacktrace

internal val tailCallDeoptimize = enabled && _settingsProvider!!.tailCallDeoptimize

internal val recoveryExplicitStacktraceTimeoutMs =
    if (tailCallDeoptimize) _settingsProvider!!.recoveryExplicitStacktraceTimeoutMs else 0U

internal val methodsNumberThreshold = if (enabled) _settingsProvider!!.methodsNumberThreshold else 0

internal val restoreCoroutineStackFrames = enabled && _settingsProvider!!.restoreCoroutineStackFrames

internal val fillUnknownElementsWithClassName = enabled && _settingsProvider!!.fillUnknownElementsWithClassName

internal var baseContinuationAccessor: BaseContinuationAccessor? = null

@Suppress("ObjectPropertyName")
private val _methodHandleInvoker: MethodHandleInvoker? =
    if (enabled) {
        loadMandatoryService<MethodHandleInvoker>()
    } else {
        null
    }

@Suppress("ObjectPropertyName")
private val _baseContinuationAccessorProvider: BaseContinuationAccessorProvider? =
    if (enabled) {
        loadMandatoryService<BaseContinuationAccessorProvider>()
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

val settingsProvider: DecoroutinatorRuntimeSettingsProvider
    get() = _settingsProvider!!

val methodHandleInvoker: MethodHandleInvoker
    get() = _methodHandleInvoker!!

val baseContinuationAccessorProvider: BaseContinuationAccessorProvider
    get() = _baseContinuationAccessorProvider!!

internal val stacktraceElementsFactory: StacktraceElementsFactory
    get() = _stacktraceElementsFactory!!

internal val specMethodsRegistry: SpecMethodsRegistry
    get() = _specMethodsRegistry!!

internal val varHandleInvoker: VarHandleInvoker
    get() = _varHandleInvoker!!

private fun supportsMethodHandle(): Boolean {
    return try {
        _supportsMethodHandle().verify()
        true
    } catch (_: Throwable) {
        false
    }
}

@Suppress("ClassName")
@AndroidKeep
internal class _supportsMethodHandle {
    @Suppress("NewApi")
    fun verify() {
        val lookup = MethodHandles.lookup()
        val handle = lookup.findVirtual(
            _supportsMethodHandle::class.java,
            ::verify.name,
            MethodType.methodType(Void.TYPE)
        )
        assert { handle != null }
    }
}
