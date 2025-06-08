@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorApi
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorBaseContinuationAccessor
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import java.io.InputStream
import java.lang.invoke.MethodHandle
import java.lang.invoke.VarHandle

data class StacktraceElement(
    val className: String,
    val fileName: String?,
    val methodName: String,
    val lineNumber: Int,
)

fun interface SpecMethodsRegistry {
    fun getSpecMethodFactoriesByStacktraceElement(
        elements: Set<StacktraceElement>
    ): Map<StacktraceElement, SpecMethodsFactory>
}

@DecoroutinatorApi
data class SpecAndItsMethodHandle(
    val specMethodHandle: MethodHandle,
    val spec: DecoroutinatorSpec
)

fun interface SpecMethodsFactory {
    fun getSpecAndItsMethodHandle(
        accessor: DecoroutinatorBaseContinuationAccessor,
        element: StacktraceElement,
        nextContinuation: BaseContinuation,
        nextSpec: SpecAndItsMethodHandle?
    ): SpecAndItsMethodHandle
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

@DecoroutinatorApi
interface MethodHandleInvoker {
    val unknownSpecMethodHandle: MethodHandle
    fun callSpecMethod(handle: MethodHandle, spec: DecoroutinatorSpec, result: Any?): Any?
    val unknownSpecMethodClass: Class<*>
    val supportsVarHandle: Boolean
}

@DecoroutinatorApi
interface VarHandleInvoker {
    fun getIntVar(handle: VarHandle, owner: BaseContinuation): Int
}

internal data class StacktraceElements(
    val elementsByContinuation: Map<BaseContinuation, StacktraceElement>,
    val possibleElements: Set<StacktraceElement>
)

internal interface StacktraceElementsFactory {
    fun getStacktraceElements(continuations: Collection<BaseContinuation>): StacktraceElements
    fun getLabelExtractor(continuation: BaseContinuation): LabelExtractor

    fun interface LabelExtractor {
        fun getLabel(continuation: BaseContinuation): Int
    }
}

internal const val UNKNOWN_LABEL = -1
