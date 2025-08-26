package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessorProvider
import dev.reformator.stacktracedecoroutinator.provider.internal.AndroidKeep
import dev.reformator.stacktracedecoroutinator.runtimesettings.internal.getRuntimeSettingsValue
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

internal val supportsMethodHandle = supportsMethodHandle()

internal val enabled =
    supportsMethodHandle && getRuntimeSettingsValue({ enabled }) {
        System.getProperty("dev.reformator.stacktracedecoroutinator.enabled", "true").toBoolean()
    }

internal val recoveryExplicitStacktrace =
    enabled && getRuntimeSettingsValue({ recoveryExplicitStacktrace }) {
        System.getProperty(
            "dev.reformator.stacktracedecoroutinator.recoveryExplicitStacktrace",
            "true"
        ).toBoolean()
    }

internal val tailCallDeoptimize =
    enabled && getRuntimeSettingsValue({ tailCallDeoptimize }) {
        System.getProperty("dev.reformator.stacktracedecoroutinator.tailCallDeoptimize", "true").toBoolean()
    }

internal val recoveryExplicitStacktraceTimeoutMs =
    if (tailCallDeoptimize) {
        getRuntimeSettingsValue({ recoveryExplicitStacktraceTimeoutMs }) {
            System.getProperty(
                "dev.reformator.stacktracedecoroutinator.recoveryExplicitStacktraceTimeoutMs",
                "500"
            ).toUInt()
        }
    } else {
        0U
    }

internal val methodsNumberThreshold =
    if (enabled) {
        getRuntimeSettingsValue({ methodsNumberThreshold }) {
            System.getProperty(
                "dev.reformator.stacktracedecoroutinator.methodsNumberThreshold",
                "50"
            ).toInt()
        }
    } else {
        0
    }

internal val fillUnknownElementsWithClassName =
    enabled && getRuntimeSettingsValue({ fillUnknownElementsWithClassName }) {
        System.getProperty(
            "dev.reformator.stacktracedecoroutinator.fillUnknownElementsWithClassName",
            "true"
        ).toBoolean()
    }

internal val isUsingElementFactoryForBaseContinuationEnabled: Boolean =
    enabled && getRuntimeSettingsValue({ isUsingElementFactoryForBaseContinuationEnabled }) {
        System.getProperty(
            "dev.reformator.stacktracedecoroutinator.isUsingElementFactoryForBaseContinuationEnabled",
            "true"
        ).toBoolean()
    }

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
