package dev.reformator.stacktracedecoroutinator.jvmagentcommon

import dev.reformator.stacktracedecoroutinator.common.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.jvmcommon.loadResource
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.lang.instrument.Instrumentation
import kotlin.coroutines.suspendCoroutine

val BASE_CONTINUATION_INTERNAL_CLASS_NAME = BASE_CONTINUATION_CLASS_NAME.replace('.', '/')
const val REGISTER_LOOKUP_METHOD_NAME = "\$decoroutinatorRegisterLookup"
private val debugMetadataAnnotationClassDescriptor = Type.getDescriptor(JavaUtilsImpl.metadataAnnotationClass)

@Target(AnnotationTarget.CLASS)
@Retention
internal annotation class DecoroutinatorAgentTransformedMarker(
    val fileNamePresent: Boolean = true,
    val fileName: String = "",
    val methodNames: Array<String>,
    val lineNumbersCounts: IntArray,
    val lineNumbers: IntArray
)

val Class<*>.isDecoroutinatorAgentTransformed: Boolean
    get() = isAnnotationPresent(DecoroutinatorAgentTransformedMarker::class.java)

internal interface JavaUtils {
    fun getDebugMetadataInfo(className: String): DebugMetadataInfo?
}

data class DebugMetadataInfo(
    val internalClassName: String,
    val methodName: String,
    val fileName: String?,
    val lineNumbers: Set<Int>
)

fun addDecoroutinatorClassFileTransformers(inst: Instrumentation) {
    inst.addTransformer(
        DecoroutinatorBaseContinuationClassFileTransformer,
        decoroutinatorJvmAgentRegistry.isBaseContinuationRetransformationAllowed && inst.isRetransformClassesSupported
    )
    Class.forName(BASE_CONTINUATION_CLASS_NAME)
    val stubClassName = _preloadStub::class.java.name
    val stubClassPath = stubClassName.replace('.', '/') + ".class"
    val stubClassBody = loadResource(stubClassPath)!!
    val stubClassInternalName = stubClassName.replace('.', '/')
    DecoroutinatorClassFileTransformer.transform(
        loader = null,
        internalClassName = stubClassInternalName,
        classBeingRedefined = null,
        protectionDomain = null,
        classBody = stubClassBody
    )
    inst.addTransformer(
        DecoroutinatorClassFileTransformer,
        decoroutinatorJvmAgentRegistry.isRetransformationAllowed && inst.isRetransformClassesSupported
    )
}

internal fun ClassNode.getDebugMetadataInfo(): DebugMetadataInfo? {
    visibleAnnotations.orEmpty().forEach { annotation ->
        if (annotation.desc == debugMetadataAnnotationClassDescriptor) {
            val parameters = annotation.values
                .chunked(2) { it[0] as String to it[1] as Any }
                .toMap()
            val internalClassName = (parameters["c"] as String).replace('.', '/')
            val methodName = parameters["m"] as String
            val fileName = (parameters["f"] as String).ifEmpty { null }
            @Suppress("UNCHECKED_CAST") val lineNumbers = (parameters["l"] as List<Int>).toSet()
            if (lineNumbers.isEmpty()) {
                return null
            }
            return DebugMetadataInfo(
                internalClassName = internalClassName,
                methodName = methodName,
                fileName = fileName,
                lineNumbers = lineNumbers,
            )
        }
    }
    return null
}

@Suppress("ClassName")
private class _preloadStub {
    @Suppress("Unused")
    suspend fun suspendFun() {
        suspendCoroutine<Unit> { }
    }
}
