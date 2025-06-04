@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal

import dev.reformator.stacktracedecoroutinator.common.internal.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.generator.internal.ClassBodyTransformationStatus
import dev.reformator.stacktracedecoroutinator.generator.internal.DebugMetadataInfo
import dev.reformator.stacktracedecoroutinator.generator.internal.getDebugMetadataInfoFromClass
import dev.reformator.stacktracedecoroutinator.generator.internal.getDebugMetadataInfoFromClassBody
import dev.reformator.stacktracedecoroutinator.generator.internal.getResourceAsStream
import dev.reformator.stacktracedecoroutinator.generator.internal.loadResource
import dev.reformator.stacktracedecoroutinator.generator.internal.needTransformation
import dev.reformator.stacktracedecoroutinator.generator.internal.transformClassBody
import dev.reformator.stacktracedecoroutinator.provider.providerApiClass
import dev.reformator.stacktracedecoroutinator.runtimesettings.DecoroutinatorMetadataInfoResolveStrategy
import org.objectweb.asm.Type
import java.io.ByteArrayInputStream
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine

fun addDecoroutinatorTransformer(inst: Instrumentation) {
    val transformer = DecoroutinatorClassFileTransformer(inst)
    Class.forName(Continuation::class.java.name)
    Class.forName(providerApiClass.name)
    Class.forName(diClass.name)
    inst.addTransformer(transformer, inst.isRetransformClassesSupported)
    Class.forName(BASE_CONTINUATION_CLASS_NAME)
    val stubClassPath = _preloadStub::class.java.name.replace('.', '/') + ".class"
    transformer.transform(
        loader = null,
        internalClassName = Type.getInternalName(_preloadStub::class.java),
        classBeingRedefined = null,
        protectionDomain = null,
        classfileBuffer = loadResource(stubClassPath)!!
    )
}

private class DecoroutinatorClassFileTransformer(
    private val inst: Instrumentation
): ClassFileTransformer {
    override fun transform(
        loader: ClassLoader?,
        internalClassName: String,
        classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?,
        classfileBuffer: ByteArray
    ): ByteArray? =
        transform(
            loader = loader,
            classBeingRedefined = classBeingRedefined,
            classfileBuffer = classfileBuffer
        ).updatedBody


    override fun transform(
        module: Module,
        loader: ClassLoader?,
        internalClassName: String,
        classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?,
        classfileBuffer: ByteArray
    ): ByteArray? {
        val transformationStatus = transform(
            loader = loader,
            classBeingRedefined = classBeingRedefined,
            classfileBuffer = classfileBuffer
        )
        if (transformationStatus.needReadProviderModule && inst.isModifiableModule(module)) {
            inst.redefineModule(
                module,
                setOf(providerApiClass.module),
                emptyMap(),
                emptyMap(),
                emptySet(),
                emptyMap()
            )
        }
        return transformationStatus.updatedBody
    }

    private fun transform(
        loader: ClassLoader?,
        classBeingRedefined: Class<*>?,
        classfileBuffer: ByteArray
    ): ClassBodyTransformationStatus {
        if (loader == null || !loader.hasProviderApiDependency) {
            return ClassBodyTransformationStatus(
                updatedBody = null,
                needReadProviderModule = false
            )
        }
        if (classBeingRedefined != null) {
            val needTransformation = classBeingRedefined.needTransformation
            if (needTransformation.needTransformation && inst.isRedefineClassesSupported) {
                if (classBeingRedefined.name == BASE_CONTINUATION_CLASS_NAME) {
                    if (!isBaseContinuationRedefinitionAllowed) {
                        return ClassBodyTransformationStatus(
                            updatedBody = null,
                            needReadProviderModule = needTransformation.needReadProviderModule
                        )
                    }
                } else {
                    if (!isRedefinitionAllowed) {
                        return ClassBodyTransformationStatus(
                            updatedBody = null,
                            needReadProviderModule = needTransformation.needReadProviderModule
                        )
                    }
                }
            } else {
                return ClassBodyTransformationStatus(
                    updatedBody = null,
                    needReadProviderModule = needTransformation.needReadProviderModule
                )
            }
        }
        return transformClassBody(
            classBody = ByteArrayInputStream(classfileBuffer),
            skipSpecMethods = false,
            metadataResolver = metadataInfoResolveStrategy
        )
    }
}

private val ClassLoader.hasProviderApiDependency: Boolean
    get() = try {
        loadClass(providerApiClass.name)
        true
    } catch (_: ClassNotFoundException) {
        false
    }

@Suppress("ClassName")
private class _preloadStub {
    @Suppress("Unused")
    suspend fun suspendFun() {
        suspendCoroutine<Unit> { }
    }
}

internal val DecoroutinatorMetadataInfoResolveStrategy.resolveFunction: (className: String) -> DebugMetadataInfo?
    get() = when (this) {
        DecoroutinatorMetadataInfoResolveStrategy.SYSTEM_RESOURCE -> { className ->
            val path = className.replace('.', '/') + ".class"
            getResourceAsStream(path)?.let { resource ->
                resource.use {
                    getDebugMetadataInfoFromClassBody(resource)
                }
            }
        }

        DecoroutinatorMetadataInfoResolveStrategy.CLASS -> { className ->
            val clazz = try { Class.forName(className) } catch (_: ClassNotFoundException) { null }
            clazz?.let { getDebugMetadataInfoFromClass(it) }
        }

        DecoroutinatorMetadataInfoResolveStrategy.SYSTEM_RESOURCE_AND_CLASS -> {
            val systemResource = DecoroutinatorMetadataInfoResolveStrategy.SYSTEM_RESOURCE.resolveFunction
            val classResource = DecoroutinatorMetadataInfoResolveStrategy.CLASS.resolveFunction
            { className ->
                systemResource(className) ?: classResource(className)
            }
        }
    }
