@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.jvmagentcommon

import dev.reformator.stacktracedecoroutinator.generator.internal.loadResource
import dev.reformator.stacktracedecoroutinator.generator.internal.tryTransformForDecoroutinator
import dev.reformator.stacktracedecoroutinator.runtime.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.runtime.isDecoroutinatorBaseContinuation
import dev.reformator.stacktracedecoroutinator.runtime.isDecoroutinatorTransformed
import org.objectweb.asm.Type
import java.io.ByteArrayInputStream
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain
import kotlin.coroutines.suspendCoroutine

fun addDecoroutinatorTransformer(inst: Instrumentation) {
    val isRetransformationSupported = inst.isRetransformClassesSupported
    val transformer = DecoroutinatorClassFileTransformer(isRetransformationSupported)
    inst.addTransformer(transformer, isRetransformationSupported)
    Class.forName(BASE_CONTINUATION_CLASS_NAME)
    val stubClassName = _preloadStub::class.java.name
    val stubClassPath = stubClassName.replace('.', '/') + ".class"
    transformer.transform(
        loader = null,
        internalClassName = Type.getInternalName(_preloadStub::class.java),
        classBeingRedefined = null,
        protectionDomain = null,
        classBody = loadResource(stubClassPath)!!
    )
}

private val baseContinuationInternalClassName = BASE_CONTINUATION_CLASS_NAME.replace('.', '/')

private class DecoroutinatorClassFileTransformer(
    private val isRetransformSupported: Boolean
): ClassFileTransformer {
    override fun transform(
        loader: ClassLoader?,
        internalClassName: String,
        classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?,
        classBody: ByteArray
    ): ByteArray? {
        val isBaseContinuation = internalClassName == baseContinuationInternalClassName
        if (classBeingRedefined != null) {
            if (!isRetransformSupported) {
                return null
            }
            if (isBaseContinuation) {
                if (classBeingRedefined.isDecoroutinatorBaseContinuation || !isBaseContinuationRetransformationAllowed) {
                    return null
                }
            } else {
                if (classBeingRedefined.isDecoroutinatorTransformed || !isRetransformationAllowed) {
                    return null
                }
            }
        } else {
            if (isBaseContinuation) {
                if (!isBaseContinuationTransformationAllowed) {
                    return null
                }
            } else {
                if (!isTransformationAllowed) {
                    return null
                }
            }
        }
        return tryTransformForDecoroutinator(
            className = Type.getObjectType(internalClassName).className,
            classBody = ByteArrayInputStream(classBody),
            metadataResolver = jvmAgentDebugMetadataInfoResolveStrategy
        )
    }
}

@Suppress("ClassName")
private class _preloadStub {
    @Suppress("Unused")
    suspend fun suspendFun() {
        suspendCoroutine<Unit> { }
    }
}
