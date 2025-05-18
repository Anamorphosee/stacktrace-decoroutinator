@file:Suppress("NewApi", "PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.bytecodeprocessor.intrinsics.ChangeClassName
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.common.intrinsics._Assertions
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorTransformed
import java.io.InputStream
import java.lang.invoke.MethodType
import java.util.ServiceConfigurationError
import java.util.ServiceLoader
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.jvm.internal.CoroutineStackFrame

const val BASE_CONTINUATION_CLASS_NAME = "kotlin.coroutines.jvm.internal.BaseContinuationImpl"

const val UNKNOWN_LINE_NUMBER = 0

val specMethodType = MethodType.methodType(
    Object::class.java,
    DecoroutinatorSpec::class.java,
    Object::class.java
)

inline fun assert(check: () -> Boolean) {
    if (_Assertions.ENABLED && !check()) {
        throw AssertionError()
    }
}

val Class<*>.isTransformed: Boolean
    get() = getDeclaredAnnotation(DecoroutinatorTransformed::class.java) != null

fun parseTransformationMetadata(
    fileNamePresent: Boolean?,
    fileName: String?,
    methodNames: List<String>,
    lineNumbersCounts: List<Int>,
    lineNumbers: List<Int>,
    baseContinuationClasses: Set<String>,
    skipSpecMethods: Boolean?
): TransformationMetadata {
    val lineNumberIterator = lineNumbers.iterator()
    return TransformationMetadata(
        fileName = if (fileNamePresent == null || fileNamePresent) {
            fileName!!
        } else {
            null
        },
        methods = methodNames.mapIndexed { index, methodName ->
            TransformationMetadata.Method(
                name = methodName,
                lineNumbers = IntArray(lineNumbersCounts[index]) { lineNumberIterator.next() }
            )
        },
        baseContinuationClasses = baseContinuationClasses,
        skipSpecMethods = skipSpecMethods ?: false
    )
}

inline fun <reified T: Any> loadService(): T? {
    val iter = ServiceLoader.load(T::class.java).iterator()
    while (true) {
        try {
            return if (iter.hasNext()) iter.next() else null
        } catch (_: ServiceConfigurationError) { }
    }
}

inline fun <reified T: Any> loadMandatoryService(): T {
    val iter = ServiceLoader.load(T::class.java).iterator()
    val errors = mutableListOf<ServiceConfigurationError>()
    while (true) {
        try {
            if (!iter.hasNext()) {
                break
            }
            return iter.next()
        } catch (e: ServiceConfigurationError) {
            errors.add(e)
        }
    }
    val message = "service [${T::class.simpleName}] not found"
    val exception = if (errors.isNotEmpty()) {
        IllegalStateException(message, errors[0])
    } else {
        IllegalStateException(message)
    }
    errors.asSequence().drop (1).forEach {
        exception.addSuppressed(it)
    }
    throw exception
}

internal fun Class<*>.getBodyStream(loader: ClassLoader): InputStream? =
    loader.getResourceAsStream(name.replace('.', '/') + ".class")

internal fun Class<*>.getBodyStream(): InputStream? =
    classLoader?.let { getBodyStream(it) }

internal const val ENABLED_PROPERTY = "dev.reformator.stacktracedecoroutinator.enabled"

@ChangeClassName(toName = "kotlin.coroutines.jvm.internal.ContinuationImpl", deleteAfterChanging = true)
internal abstract class ContinuationImpl(
    @Suppress("UNUSED_PARAMETER") completion: Continuation<Any?>?
): BaseContinuation(), CoroutineStackFrame {
    protected abstract fun invokeSuspend(result: Any?): Any?

    override val context: CoroutineContext
        get() { fail() }

    override val callerFrame: CoroutineStackFrame?
        get() { fail() }

    override fun getStackTraceElement(): StackTraceElement? { fail() }
}

internal class DecoroutinatorContinuationImpl(
    completion: Continuation<Any?>,
    val fileName: String?,
    val className: String,
    val methodName: String,
    val lineNumber: Int
): ContinuationImpl(completion) {
    override fun invokeSuspend(result: Any?): Any? =
        result

    override fun getStackTraceElement(): StackTraceElement =
        StackTraceElement(
            className,
            methodName,
            fileName,
            lineNumber
        )
}
