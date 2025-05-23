@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorTransformed
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

@DecoroutinatorTransformed(
    methodNames = [],
    lineNumbersCounts = [],
    lineNumbers = [],
    baseContinuationClasses = [],
    marker = true
)
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

interface CommonSettingsProvider {
    val decoroutinatorEnabled: Boolean
        get() = System.getProperty(ENABLED_PROPERTY, "true").toBoolean()

    val recoveryExplicitStacktrace: Boolean
        get() = System.getProperty("dev.reformator.stacktracedecoroutinator.recoveryExplicitStacktrace", "true")
            .toBoolean()

    val tailCallDeoptimize: Boolean
        get() = System.getProperty("dev.reformator.stacktracedecoroutinator.tailCallDeoptimize", "true")
            .toBoolean()

    val recoveryExplicitStacktraceTimeoutMs: Long
        get() = System.getProperty("dev.reformator.stacktracedecoroutinator.recoveryExplicitStacktraceTimeoutMs", "500")
            .toLong()

    companion object: CommonSettingsProvider
}

@DecoroutinatorTransformed(
    methodNames = [],
    lineNumbersCounts = [],
    lineNumbers = [],
    baseContinuationClasses = [],
    marker = true
)
interface MethodHandleInvoker {
    fun createSpec(
        cookie: Cookie,
        lineNumber: Int,
        nextSpecAndItsMethod: SpecAndItsMethodHandle?,
        nextContinuation: BaseContinuation
    ): DecoroutinatorSpec
    val unknownSpecMethodHandle: MethodHandle
    fun callInvokeSuspend(continuation: BaseContinuation, cookie: Cookie, specResult: Any?): Any?
    fun callSpecMethod(handle: MethodHandle, spec: DecoroutinatorSpec, result: Any?): Any?
    val unknownSpecMethodClass: Class<*>
    val supportsVarHandle: Boolean
}

@DecoroutinatorTransformed(
    methodNames = [],
    lineNumbersCounts = [],
    lineNumbers = [],
    baseContinuationClasses = [],
    marker = true
)
interface VarHandleInvoker {
    fun getIntVar(handle: VarHandle, owner: BaseContinuation): Int
}

@DecoroutinatorTransformed(
    methodNames = [],
    lineNumbersCounts = [],
    lineNumbers = [],
    baseContinuationClasses = [],
    marker = true
)
class Cookie(
    val invokeSuspendHandle: MethodHandle,
    val releaseInterceptedHandle: MethodHandle
)

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
