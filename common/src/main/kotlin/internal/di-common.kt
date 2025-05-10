package dev.reformator.stacktracedecoroutinator.common.internal

import java.util.ServiceLoader

val methodHandleInvoker: MethodHandleInvoker =
    ServiceLoader.load(MethodHandleInvoker::class.java).iterator().next()

val varHandleInvoker: VarHandleInvoker? =
    if (methodHandleInvoker.supportsVarHandle) {
        ServiceLoader.load(VarHandleInvoker::class.java).iterator().next()
    } else {
        null
    }

internal val settingsProvider = ServiceLoader.load(CommonSettingsProvider::class.java).firstOrNull() ?:
    object: CommonSettingsProvider {}

internal val enabled = settingsProvider.decoroutinatorEnabled
internal val recoveryExplicitStacktrace = enabled && settingsProvider.recoveryExplicitStacktrace
internal val tailCallDeoptimize = enabled && settingsProvider.tailCallDeoptimize

internal var cookie: Cookie? = null

internal val stacktraceElementsFactory: StacktraceElementsFactory = StacktraceElementsFactoryImpl

internal val specMethodsRegistry: SpecMethodsRegistry =
    ServiceLoader.load(SpecMethodsRegistry::class.java).firstOrNull() ?: SpecMethodsRegistryImpl

internal val annotationMetadataResolver: AnnotationMetadataResolver? =
    ServiceLoader.load(AnnotationMetadataResolver::class.java).firstOrNull()
