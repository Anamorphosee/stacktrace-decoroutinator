@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.provider.internal.AndroidLegacyKeep
import java.io.InputStream
import java.lang.invoke.MethodHandle
import java.lang.invoke.VarHandle

fun interface SpecMethodsFactory {
    fun getSpecMethodHandle(element: StackTraceElement): MethodHandle?
}

data class TransformationMetadata(
    val fileName: String?,
    val methods: List<Method>,
    val baseContinuationClasses: Set<String>,
    val skipSpecMethods: Boolean
) {
    @Suppress("ArrayInDataClass")
    data class Method(
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
    fun getIntVar(handle: VarHandle, owner: BaseContinuation): Int
}

internal interface StacktraceElementsFactory {
    fun getStacktraceElement(baseContinuation: BaseContinuation): StackTraceElement?
    fun getLabelExtractor(continuation: BaseContinuation): LabelExtractor

    fun interface LabelExtractor {
        fun getLabel(continuation: BaseContinuation): Int
    }
}

internal const val UNKNOWN_LABEL = -1
