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
import java.util.concurrent.locks.Lock
import kotlin.concurrent.withLock
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

internal fun <K: Any, V: Any> MutableMap<K, V>.optimisticLockGet(key: K, notSetValue: V, lock: Lock): V? {
    val result = try {
        get(key)
    } catch (_: ConcurrentModificationException) { null } ?: lock.withLock {
        get(key)?.let { return@withLock it }
        set(key, notSetValue)
        notSetValue
    }
    return if (result === notSetValue) null else result
}

internal inline fun <K: Any, V: Any> MutableMap<K, V>.optimisticLockGetOrPut(key: K, lock: Lock, generator: () -> V): V =
    try {
        get(key)
    } catch (_: ConcurrentModificationException) { null } ?: lock.withLock {
        get(key)?.let { return@withLock it }
        val newValue = generator()
        set(key, newValue)
        newValue
    }

internal class CompactMap<K, V>: AbstractMutableMap<K, V>() {
    private data class Entry<K, V>(override val key: K, override var value: V): MutableMap.MutableEntry<K, V> {
        override fun setValue(newValue: V): V {
            val oldValue = value
            value = newValue
            return oldValue
        }
    }

    private var _entries = emptyArray<Entry<K, V>>()

    override fun put(key: K, value: V): V? {
        _entries.forEach { entry ->
            if (entry.key == key) {
                val oldValue = entry.value
                entry.value = value
                return oldValue
            }
        }
        _entries = _entries + Entry(key, value)
        return null
    }

    override fun get(key: K): V? {
        _entries.forEach { (entryKey, entryValue) ->
            if (entryKey == key) {
                return entryValue
            }
        }
        return null
    }

    override val size: Int
        get() = _entries.size

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = object: AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
            override fun add(element: MutableMap.MutableEntry<K, V>): Boolean { TODO() }

            override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> =
                object: MutableIterator<MutableMap.MutableEntry<K, V>> {
                    private var index = 0

                    override fun hasNext(): Boolean = index < _entries.size

                    override fun next(): MutableMap.MutableEntry<K, V> {
                        if (!hasNext()) throw NoSuchElementException()
                        return _entries[index++]
                    }

                    override fun remove() { TODO() }
                }

            override val size: Int
                get() = _entries.size
        }
}

internal fun <K, V1, V2> Map<K, V1>.mapValuesCompact(threshold: Int, transform: (Map.Entry<K, V1>) -> V2): Map<K, V2> {
    val result: MutableMap<K, V2> = if (size < threshold) {
        CompactMap()
    } else {
        newHashMapForSize(size)
    }
    mapValuesTo(result, transform)
    return result
}

internal fun <K, V> newHashMapForSize(size: Int): MutableMap<K, V> =
    HashMap(size * 4 / 3 + 1)
