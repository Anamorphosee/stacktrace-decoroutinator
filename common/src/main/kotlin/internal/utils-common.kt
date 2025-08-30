@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.common.intrinsics.ContinuationImpl
import dev.reformator.stacktracedecoroutinator.common.intrinsics._Assertions
import dev.reformator.stacktracedecoroutinator.common.intrinsics.createFailure
import dev.reformator.stacktracedecoroutinator.common.intrinsics.probeCoroutineResumed
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor
import java.io.InputStream
import java.lang.invoke.MethodHandle
import java.util.ServiceLoader
import java.util.concurrent.locks.Lock
import kotlin.concurrent.withLock
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

const val BASE_CONTINUATION_CLASS_NAME = "kotlin.coroutines.jvm.internal.BaseContinuationImpl"

const val UNKNOWN_LINE_NUMBER = -1

internal class DecoroutinatorSpecImpl(
    private val accessor: BaseContinuationAccessor,
    override val lineNumber: Int,
    private val _nextSpec: DecoroutinatorSpec?,
    private val _nextSpecHandle: MethodHandle?,
    private val nextContinuation: BaseContinuation?
): DecoroutinatorSpec {
    override val isLastSpec: Boolean
        get() = _nextSpec == null

    override val nextSpecHandle: MethodHandle
        get() = _nextSpecHandle!!

    override val nextSpec: DecoroutinatorSpec
        get() = _nextSpec!!

    override fun resumeNext(result: Any?): Any? =
        if (nextContinuation != null && result !== COROUTINE_SUSPENDED) {
            nextContinuation.callInvokeSuspend(accessor, result)
        } else {
            result
        }
}

inline fun ifAssertionEnabled(check: () -> Unit) {
    if (_Assertions.ENABLED) {
        check()
    }
}

inline fun assert(check: () -> Boolean) {
    ifAssertionEnabled {
        if (!check()) {
            throw AssertionError()
        }
    }
}

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

private fun <T: Any> loadServices(type: Class<T>): Pair<List<T>, List<Throwable>> {
    val services = mutableListOf<T>()
    val errors = mutableListOf<Throwable>()
    val iter = ServiceLoader.load(type).iterator()
    while (true) {
        try {
            if (!iter.hasNext()) {
                break
            }
            services.add(iter.next())
        } catch (e: Throwable) {
            errors.add(e)
        }
    }
    return Pair(services, errors)
}

private fun <T: Any> loadService(type: Class<T>): T? =
    loadServices(type).first.firstOrNull()

internal inline fun <reified T: Any> loadService(): T? =
    loadService(T::class.java)

private fun <T: Any> loadMandatoryService(type: Class<T>): T {
    val services = loadServices(type)
    if (services.first.isNotEmpty()) {
        return services.first.first()
    }
    val message = "service [${type.simpleName}] not found"
    val exception = if (services.second.isNotEmpty()) {
        IllegalStateException(message, services.second[0])
    } else {
        IllegalStateException(message)
    }
    services.second.asSequence().drop (1).forEach {
        exception.addSuppressed(it)
    }
    throw exception
}

internal inline fun <reified T: Any> loadMandatoryService(): T =
    loadMandatoryService(T::class.java)

internal fun Class<*>.getBodyStream(loader: ClassLoader): InputStream? =
    loader.getResourceAsStream(name.replace('.', '/') + ".class")

internal fun Class<*>.getBodyStream(): InputStream? =
    classLoader?.let { getBodyStream(it) }

internal const val ENABLED_PROPERTY = "dev.reformator.stacktracedecoroutinator.enabled"

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
    HashMap(getHashMapCapacityForSize(size))

private fun getHashMapCapacityForSize(size: Int): Int =
    if (size < 3) 3 else (size * 4 / 3 + 1)

@Suppress("NOTHING_TO_INLINE")
internal inline fun BaseContinuation.callInvokeSuspend(
    accessor: BaseContinuationAccessor,
    result: Any?
): Any? {
    probeCoroutineResumed(this)
    val newResult = try {
        accessor.invokeSuspend(this, result)
    } catch (exception: Throwable) {
        return createFailure(exception)
    }
    if (newResult === COROUTINE_SUSPENDED) {
        return newResult
    }
    accessor.releaseIntercepted(this)
    return newResult
}

internal val StackTraceElement.normalizedLineNumber: Int
    get() = if (lineNumber < 0) UNKNOWN_LINE_NUMBER else lineNumber
