package dev.reformator.stacktracedecoroutinator.registry

import dev.reformator.stacktracedecoroutinator.utils.invokeStacktraceMethodType
import dev.reformator.stacktracedecoroutinator.utils.lookup
import java.lang.invoke.MethodHandle
import java.util.concurrent.ConcurrentHashMap

data class MethodKey(
    val className: String,
    val fileName: String?,
    val methodName: String
)

typealias DecoroutinatorStacktraceClassGenerator = (
    className: String,
    fileName: String?,
    classRevision: Int,
    methodName2LineNumbers: Map<String, Set<Int>>
) -> Class<*>

interface DecoroutinatorStacktraceMethodHandleRegistry {
    fun getStacktraceMethodHandles(method2LineNumbers: Map<MethodKey, Collection<Int>>): Map<MethodKey, MethodHandle>
}

class DecoroutinatorStacktraceMethodHandleRegistryImpl(
    private val stacktraceClassGenerator: DecoroutinatorStacktraceClassGenerator
): DecoroutinatorStacktraceMethodHandleRegistry {
    private val className2Spec = ConcurrentHashMap<String, ClassSpec>()

    override fun getStacktraceMethodHandles(method2LineNumbers: Map<MethodKey, Collection<Int>>): Map<MethodKey, MethodHandle> {
        val result = mutableMapOf<MethodKey, MethodHandle>()

        val missingKeys = buildSet {
            method2LineNumbers.forEach { (key, lineNumbers) ->
                if (lineNumbers.isEmpty()) {
                    throw IllegalArgumentException("empty lineNumbers for key $key")
                }
                val classSpec = className2Spec[key.className]
                if (classSpec == null) {
                    add(key)
                    return@forEach
                }
                if (classSpec.fileName != key.fileName) {
                    throw IllegalStateException("different file names for class [${key.className}]: [${classSpec.fileName}] and [${key.fileName}]")
                }
                val methodSpec = classSpec.methodName2Spec[key.methodName]
                if (methodSpec == null) {
                    add(key)
                    return@forEach
                }
                if (!methodSpec.lineNumbers.containsAll(lineNumbers)) {
                    add(key)
                    return@forEach
                }
                result[key] = methodSpec.handle
            }
        }

        if (missingKeys.isNotEmpty()) {
            missingKeys.groupBy { it.className }.forEach { (className, keys) ->
                val fileName = keys.asSequence()
                    .map { it.fileName }
                    .distinct()
                    .single()
                val classSpec = className2Spec.computeIfAbsent(className) { ClassSpec(fileName) }
                if (classSpec.fileName != fileName) {
                    throw IllegalStateException("different file names for class [$className]: [${classSpec.fileName}] and [$fileName]")
                }
                synchronized(classSpec) {
                    val methodName2LineNumbers = mutableMapOf<String, Set<Int>>()
                    keys.forEach {
                        val methodSpec = classSpec.methodName2Spec[it.methodName]
                        if (methodSpec == null) {
                            methodName2LineNumbers[it.methodName] = method2LineNumbers[it]!!.toSet()
                            return@forEach
                        }
                        val newLineNumbers = method2LineNumbers[it]!!.toSet() - methodSpec.lineNumbers
                        if (newLineNumbers.isNotEmpty()) {
                            methodName2LineNumbers[it.methodName] = newLineNumbers
                        }
                    }
                    if (methodName2LineNumbers.isEmpty()) {
                        return@forEach
                    }
                    classSpec.methodName2Spec.forEach { (methodName, methodSpec) ->
                        methodName2LineNumbers[methodName] =
                            methodName2LineNumbers[methodName].orEmpty() + methodSpec.lineNumbers
                    }
                    val stacktraceClass = stacktraceClassGenerator(className, fileName, classSpec.revision + 1, methodName2LineNumbers)
                    methodName2LineNumbers.forEach { (methodName, lineNumbers) ->
                        val handle = lookup.findStatic(stacktraceClass, methodName, invokeStacktraceMethodType)
                        classSpec.methodName2Spec[methodName] = MethodSpec(lineNumbers, handle)
                    }
                    classSpec.revision++
                }
                keys.forEach {
                    result[it] = classSpec.methodName2Spec[it.methodName]!!.handle
                }
            }
        }

        return result
    }
}

private class ClassSpec(
    val fileName: String?
) {
    var revision = -1
    val methodName2Spec = ConcurrentHashMap<String, MethodSpec>()
}

private data class MethodSpec(
    val lineNumbers: Set<Int>,
    val handle: MethodHandle
)
