@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.bytecodeprocessor.intrinsics.ChangeClassName
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.common.intrinsics._Assertions
import dev.reformator.stacktracedecoroutinator.common.intrinsics.createFailure
import dev.reformator.stacktracedecoroutinator.common.intrinsics.probeCoroutineResumed
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorTransformed
import java.io.InputStream
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodType
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.jvm.internal.CoroutineStackFrame

const val BASE_CONTINUATION_CLASS_NAME = "kotlin.coroutines.jvm.internal.BaseContinuationImpl"

const val TRANSFORMED_VERSION = 0

const val UNKNOWN_LINE_NUMBER = 0

val specMethodType = MethodType.methodType(
    Object::class.java,
    DecoroutinatorSpec::class.java,
    Object::class.java
)

class DecoroutinatorSpecImpl(
    private val cookie: Cookie,
    override val lineNumber: Int,
    private val nextSpecAndItsMethod: SpecAndItsMethodHandle?,
    private val nextContinuation: BaseContinuation
): DecoroutinatorSpec {
    override val isLastSpec: Boolean
        get() = nextSpecAndItsMethod == null

    override val nextSpecHandle: MethodHandle
        get() = nextSpecAndItsMethod!!.specMethodHandle

    override val nextSpec: DecoroutinatorSpec
        get() = nextSpecAndItsMethod!!.spec

    override val coroutineSuspendedMarker: Any
        get() = COROUTINE_SUSPENDED

    override fun resumeNext(result: Any?): Any? =
        nextContinuation.callInvokeSuspend(cookie, result)
}

inline fun assert(check: () -> Boolean) {
    if (_Assertions.ENABLED && !check()) {
        throw AssertionError()
    }
}

val Class<*>.isTransformed: Boolean
    get() {
        val transformed = getDeclaredAnnotation(DecoroutinatorTransformed::class.java) ?: return false
        if (transformed.version > TRANSFORMED_VERSION) {
            error("Class [$this] has transformed meta of version [${transformed.version}]. Please update Decoroutinator")
        }
        return transformed.version == TRANSFORMED_VERSION
    }

fun parseTransformationMetadata(
    fileNamePresent: Boolean?,
    fileName: String?,
    methodNames: List<String>,
    lineNumbersCounts: List<Int>,
    lineNumbers: List<Int>,
    baseContinuationClasses: Set<String>,
    version: Int
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
        version = version
    )
}

internal fun Class<*>.getBodyStream(loader: ClassLoader): InputStream? =
    loader.getResourceAsStream(name.replace('.', '/') + ".class")

internal fun Class<*>.getBodyStream(): InputStream? =
    classLoader?.let { getBodyStream(it) }

internal const val ENABLED_PROPERTY = "dev.reformator.stacktracedecoroutinator.enabled"

@Suppress("NOTHING_TO_INLINE")
internal inline fun BaseContinuation.callInvokeSuspend(cookie: Cookie, result: Any?): Any? {
    probeCoroutineResumed(this)
    val newResult = try {
        cookie.invokeSuspendHandle.invokeExact(this, result)
    } catch (exception: Throwable) {
        return createFailure(exception)
    }
    if (newResult === COROUTINE_SUSPENDED) {
        return newResult
    }
    cookie.releaseInterceptedHandle.invokeExact(this)
    return newResult
}

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
