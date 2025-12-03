@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("DecoroutinatorProviderApiKt")

package dev.reformator.stacktracedecoroutinator.provider

import dev.reformator.bytecodeprocessor.intrinsics.GetOwnerClass
import dev.reformator.bytecodeprocessor.intrinsics.MethodNameConstant
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.provider.internal.AndroidLegacyKeep
import dev.reformator.stacktracedecoroutinator.provider.internal.provider
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles

@AndroidLegacyKeep
interface DecoroutinatorSpec {
    val lineNumber: Int
    val isLastSpec: Boolean
    val nextSpecHandle: MethodHandle
    val nextSpec: DecoroutinatorSpec
    fun resumeNext(result: Any?): Any?
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
@Retention
annotation class DecoroutinatorTransformed(
    @Suppress("unused")
    @get:MethodNameConstant("decoroutinatorTransformedFileNamePresentMethodName")
    @get:JvmName("fnp")
    val fileNamePresent: Boolean = true,

    @get:MethodNameConstant("decoroutinatorTransformedFileNameMethodName")
    @get:JvmName("fn")
    val fileName: String = "",

    @Suppress("unused")
    @get:MethodNameConstant("decoroutinatorTransformedMethodNamesMethodName")
    @get:JvmName("mn")
    val methodNames: Array<String> = [],

    @Suppress("unused")
    @get:MethodNameConstant("decoroutinatorTransformedLineNumbersCountsMethodName")
    @get:JvmName("lnc")
    val lineNumbersCounts: IntArray = [],

    @get:MethodNameConstant("decoroutinatorTransformedLineNumbersMethodName")
    @get:JvmName("ln")
    val lineNumbers: IntArray = [],

    @get:MethodNameConstant("decoroutinatorTransformedSkipSpecMethodsMethodName")
    @get:JvmName("ssm")
    val skipSpecMethods: Boolean = false
)

interface BaseContinuationExtractor {
    @Suppress("unused", "PropertyName")
    @get:MethodNameConstant("baseContinuationExtractorGetLabelMethodName")
    val `$decoroutinator$label`: Int

    @Suppress("unused", "PropertyName")
    @get:MethodNameConstant("baseContinuationExtractorGetElementsMethodName")
    val `$decoroutinator$elements`: Array<StackTraceElement>

    @Suppress("unused", "PropertyName")
    @get:MethodNameConstant("baseContinuationExtractorGetSpecMethodsMethodName")
    val `$decoroutinator$specMethods`: Array<MethodHandle?>
}

val isDecoroutinatorEnabled: Boolean
    @MethodNameConstant("isDecoroutinatorEnabledMethodName") get() = provider.isDecoroutinatorEnabled

@Suppress("unused")
@MethodNameConstant("registerTransformedClassMethodName")
fun registerTransformedClass(lookup: MethodHandles.Lookup) {
    provider.registerTransformedClass(lookup)
}

@Suppress("unused")
@MethodNameConstant("getBaseContinuationMethodName")
fun getBaseContinuation(
    completion: Any?,
    fileName: String?,
    className: String,
    methodName: String,
    lineNumber: Int
): Any? =
    provider.getBaseContinuation(
        completion = completion,
        fileName = fileName,
        className = className,
        methodName = methodName,
        lineNumber = lineNumber
    )

val isUsingElementFactoryForBaseContinuationEnabled: Boolean
    @MethodNameConstant("isUsingElementFactoryForBaseContinuationEnabledMethodName")
    get() = provider.isUsingElementFactoryForBaseContinuationEnabled

val providerApiClass: Class<*>
    @GetOwnerClass get() { fail() }
