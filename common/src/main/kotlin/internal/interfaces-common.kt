@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import java.io.InputStream
import java.lang.invoke.MethodHandle

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

data class SpecAndItsMethodHandle(
    val specMethodHandle: MethodHandle,
    val spec: DecoroutinatorSpec
)

fun interface SpecMethodsFactory {
    fun getSpecAndItsMethodHandle(
        cookie: Cookie,
        element: StacktraceElement,
        nextContinuation: BaseContinuation,
        nextSpec: SpecAndItsMethodHandle?
    ): SpecAndItsMethodHandle
}

data class TransformationMetadata(
    val fileName: String?,
    val methods: List<Method>,
    val baseContinuationClasses: Set<String>,
    val version: Int
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

interface CommonSettingsProvider {
    val decoroutinatorEnabled: Boolean
        get() = System.getProperty(ENABLED_PROPERTY, "true").toBoolean()

    val recoveryExplicitStacktrace: Boolean
        get() = System.getProperty("dev.reformator.stacktracedecoroutinator.recoveryExplicitStacktrace", "true")
            .toBoolean()

    val tailCallDeoptimize: Boolean
        get() = System.getProperty("dev.reformator.stacktracedecoroutinator.tailCallDeoptimize", "true")
            .toBoolean()
}

class Cookie(
    val invokeSuspendHandle: MethodHandle,
    val releaseInterceptedHandle: MethodHandle
)

internal data class StacktraceElements(
    val elementsByContinuation: Map<BaseContinuation, StacktraceElement>,
    val possibleElements: Set<StacktraceElement>
)

internal fun interface StacktraceElementsFactory {
    fun getStacktraceElements(continuations: Set<BaseContinuation>): StacktraceElements
}
