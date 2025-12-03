@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.jvmagentcommon.internal

import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.bytecodeprocessor.intrinsics.ownerClass
import dev.reformator.stacktracedecoroutinator.classtransformer.internal.ClassBodyTransformationStatus
import dev.reformator.stacktracedecoroutinator.classtransformer.internal.DebugMetadataInfo
import dev.reformator.stacktracedecoroutinator.classtransformer.internal.getDebugMetadataInfoFromClass
import dev.reformator.stacktracedecoroutinator.classtransformer.internal.getDebugMetadataInfoFromClassBody
import dev.reformator.stacktracedecoroutinator.classtransformer.internal.noClassBodyTransformationStatus
import dev.reformator.stacktracedecoroutinator.classtransformer.internal.transformClassBody
import dev.reformator.stacktracedecoroutinator.intrinsics.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.provider.internal.internalName
import dev.reformator.stacktracedecoroutinator.provider.providerApiClass
import dev.reformator.stacktracedecoroutinator.runtimesettings.DecoroutinatorMetadataInfoResolveStrategy
import java.io.ByteArrayInputStream
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain
import java.util.Base64

fun addDecoroutinatorTransformer(inst: Instrumentation) {
    val transformer = DecoroutinatorClassFileTransformer(inst)
    transformer.transform(
        loader = ownerClass.classLoader,
        internalClassName = suspendClassName.internalName,
        classBeingRedefined = null,
        protectionDomain = null,
        classfileBuffer = Base64.getDecoder().decode(suspendClassBodyBase64)
    )
    inst.addTransformer(transformer, inst.isRetransformClassesSupported)
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
            return noClassBodyTransformationStatus
        }

        if (classBeingRedefined != null) {
            fun isClassRedefinitionAllowed(): Boolean {
                if (!inst.isRedefineClassesSupported) return false
                return if (classBeingRedefined.name == BASE_CONTINUATION_CLASS_NAME) {
                    isBaseContinuationRedefinitionAllowed
                } else {
                    isRedefinitionAllowed
                }
            }

            val transformationStatus = transformClassBody(
                classBody = ByteArrayInputStream(classfileBuffer),
                skipSpecMethods = false,
                metadataResolver = metadataInfoResolveStrategy
            )

            return if (transformationStatus.updatedBody == null || isClassRedefinitionAllowed()) {
                transformationStatus
            } else {
                ClassBodyTransformationStatus(
                    updatedBody = null,
                    needReadProviderModule = transformationStatus.needReadProviderModule
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
        loadClass(providerApiClass.name) == providerApiClass
    } catch (_: ClassNotFoundException) {
        false
    }

internal val DecoroutinatorMetadataInfoResolveStrategy.resolveFunction: (className: String) -> DebugMetadataInfo?
    get() = when (this) {
        DecoroutinatorMetadataInfoResolveStrategy.SYSTEM_RESOURCE -> { className ->
            val path = className.internalName + ".class"
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
            { systemResource(it) ?: classResource(it) }
        }
    }

private val suspendClassName: String
    @LoadConstant("jvmAgentCommonSuspendClassName") get() { fail() }

private val suspendClassBodyBase64: String
    @LoadConstant("jvmAgentCommonSuspendClassBodyBase64") get() { fail() }
