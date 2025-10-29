@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.provider.internal.AndroidLegacyKeep
import java.io.InputStream
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle

interface TransformedClassesRegistry {
    class TransformedClassSpec(
        val transformedClass: Class<*>,
        val fileName: String?,
        val lookup: MethodHandles.Lookup,
        val lineNumbersByMethod: Map<String, IntArray>,
        val skipSpecMethods: Boolean
    )

    fun interface Listener {
        fun onNewTransformedClass(spec: TransformedClassSpec)
        fun onException(exception: Throwable) { }
    }

    val transformedClasses: Collection<TransformedClassSpec>
    operator fun get(clazz: Class<*>): TransformedClassSpec?
    fun addListener(listener: Listener)
    fun registerTransformedClass(lookup: MethodHandles.Lookup)
}

fun interface SpecMethodsFactory {
    fun getSpecMethodHandle(element: StackTraceElement): MethodHandle?
}

data class TransformationMetadata(
    val fileName: String?,
    val methods: List<Method>,
    val skipSpecMethods: Boolean
) {
    class Method(
        val name: String,
        val lineNumbers: IntArray
    )
}

@Suppress("ArrayInDataClass")
data class KotlinDebugMetadata(
    val sourceFile: String,
    val className: String,
    val methodName: String,
    val lineNumbers: IntArray
)

interface AnnotationMetadataResolver {
    fun getTransformationMetadata(classBody: InputStream): TransformationMetadata?
    fun getKotlinDebugMetadata(classBody: InputStream): KotlinDebugMetadata?
}

@AndroidLegacyKeep
interface MethodHandleInvoker {
    val unknownSpecMethodHandle: MethodHandle
    fun callSpecMethod(handle: MethodHandle, spec: DecoroutinatorSpec, result: Any?): Any?
    val unknownSpecMethodClass: Class<*>
    val supportsVarHandle: Boolean
}

@AndroidLegacyKeep
interface VarHandleInvoker {
    fun getIntVar(handle: VarHandle, owner: Any): Int
}

internal interface StacktraceElementsFactory {
    fun getStacktraceElement(baseContinuation: BaseContinuation): StackTraceElement?
    fun getLabel(baseContinuation: BaseContinuation): Int
}

internal const val NONE_LABEL = Int.MIN_VALUE / 2
internal const val UNKNOWN_LABEL = NONE_LABEL - 1
