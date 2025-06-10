package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorAndroidKeep
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorBaseContinuationAccessor
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorBaseContinuationAccessorProvider
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

internal val supportsMethodHandle = supportsMethodHandle()

val settingsProvider =
    if (supportsMethodHandle) loadRuntimeSettingsProvider() else null

internal val enabled = supportsMethodHandle && settingsProvider!!.enabled

internal val recoveryExplicitStacktrace = enabled && settingsProvider!!.recoveryExplicitStacktrace

internal val tailCallDeoptimize = enabled && settingsProvider!!.tailCallDeoptimize

internal val recoveryExplicitStacktraceTimeoutMs =
    if (tailCallDeoptimize) settingsProvider!!.recoveryExplicitStacktraceTimeoutMs else 0U

internal val methodsNumberThreshold = if (enabled) settingsProvider!!.methodsNumberThreshold else 0

internal var baseContinuationAccessor: DecoroutinatorBaseContinuationAccessor? = null

@Suppress("ObjectPropertyName")
private val _methodHandleInvoker: MethodHandleInvoker? =
    if (enabled) {
        loadMandatoryService<MethodHandleInvoker>()
    } else {
        null
    }

@Suppress("ObjectPropertyName")
private val _baseContinuationAccessorProvider: DecoroutinatorBaseContinuationAccessorProvider? =
    if (enabled) {
        loadMandatoryService<DecoroutinatorBaseContinuationAccessorProvider>()
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

val baseContinuationAccessorProvider: DecoroutinatorBaseContinuationAccessorProvider
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
@DecoroutinatorAndroidKeep
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
