@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.runtime.internal

import java.lang.invoke.MethodHandle
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.jvm.internal.BaseContinuationImpl
import kotlin.coroutines.jvm.internal.DecoroutinatorSpecImpl

interface SpecFactory {
    fun createNotCallingNextHandle(lineNumber: Int, nextContinuation: Continuation<*>): Any

    fun createCallingNextHandle(
        lineNumber: Int,
        nextHandle: MethodHandle,
        nextSpec: Any,
        nextContinuation: Continuation<*>
    ): Any

    val handle: MethodHandle
}

interface SpecRegistry {
    fun getSpecFactories(
        elements: Collection<StacktraceElement>
    ): Map<StacktraceElement, SpecFactory>
}

abstract class BaseSpecRegistry: SpecRegistry {
    private val className2Spec = ConcurrentHashMap<String, ClassSpec>()
    @Volatile
    private var notSynchronizedClassName2Spec = emptyMap<String, NotSynchronizedClassSpec>()

    override fun getSpecFactories(
        elements: Collection<StacktraceElement>
    ): Map<StacktraceElement, SpecFactory> {
        val transformed = TransformedClassesSpecRegistry.getSpecFactories(elements)
        val notTransformedElements = elements.asSequence().filter { it !in transformed }.toSet()
        if (notTransformedElements.isEmpty()) {
            return transformed
        }

        val result: MutableMap<StacktraceElement, SpecFactory> = HashMap(transformed)
        val missingElements = getMissingElementsAndFillResult(notTransformedElements, result)
        if (missingElements.isNotEmpty()) {
            regenerateClassesForMissingElements(missingElements)
            updateNotSynchronizedClassName2Spec()

            missingElements.groupBy { it.className }.forEach { (className, missingElements) ->
                val classSpec = className2Spec[className]!!
                missingElements.groupBy { it.methodName }.forEach { (methodName, missingElements) ->
                    val methodSpec = classSpec.methodName2Spec[methodName]!!
                    missingElements.forEach {
                        result[it] = methodSpec.factory
                    }
                }
            }
        }

        return result
    }

    protected abstract fun generateSpecClassAndGetMethodName2Factory(
        className: String,
        fileName: String?,
        classRevision: Int,
        methodName2LineNumbers: Map<String, Set<Int>>
    ): Map<String, SpecFactory>

    private fun getMissingElementsAndFillResult(
        elements: Collection<StacktraceElement>,
        result: MutableMap<StacktraceElement, SpecFactory>
    ) = buildSet {
        val className2Spec = notSynchronizedClassName2Spec
        elements.groupBy { it.className }.forEach classForEach@{ (className, elements) ->
            val classSpec = className2Spec[className]
            if (classSpec == null) {
                addAll(elements)
                return@classForEach
            }

            elements.groupBy { it.methodName }.forEach methodForEach@{ (methodName, elements) ->
                val methodSpec = classSpec.methodName2Spec[methodName]
                if (methodSpec == null) {
                    addAll(elements)
                    return@methodForEach
                }

                elements.forEach {
                    if (it.fileName != classSpec.fileName) {
                        throw IllegalArgumentException(
                            "different file names for class [$className]: [${it.fileName}] and [${classSpec.fileName}]"
                        )
                    }
                    if (it.lineNumber in methodSpec.lineNumbers) {
                        result[it] = methodSpec.factory
                    } else {
                        add(it)
                    }
                }
            }
        }
    }

    private fun regenerateClassesForMissingElements(
        missingElements: Collection<StacktraceElement>
    ) = missingElements.groupBy { it.className }.forEach { (className, missingElements) ->
        val fileName = missingElements.asSequence()
            .map { it.fileName }
            .distinct()
            .single()

        val classSpec = className2Spec.computeIfAbsent(className) { ClassSpec(fileName) }
        if (classSpec.fileName != fileName) {
            throw IllegalStateException(
                "different file names for class [$className]: [${classSpec.fileName}] and [$fileName]"
            )
        }

        synchronized(classSpec) {
            val methodName2LineNumbers = mutableMapOf<String, Set<Int>>()
            missingElements.groupBy { it.methodName }.forEach classForEach@{ (methodName, missingElements) ->
                val methodSpec = classSpec.methodName2Spec[methodName]
                if (methodSpec == null) {
                    methodName2LineNumbers[methodName] = missingElements.asSequence()
                        .map { it.lineNumber }
                        .toSet()
                    return@classForEach
                }

                val missingLineNumbers = missingElements.asSequence()
                    .map { it.lineNumber }
                    .filter { it !in methodSpec.lineNumbers }
                    .toSet()
                if (missingLineNumbers.isNotEmpty()) {
                    methodName2LineNumbers[methodName] = missingLineNumbers
                }
            }

            if (methodName2LineNumbers.isNotEmpty()) {
                classSpec.methodName2Spec.forEach { (methodName, methodSpec) ->
                    methodName2LineNumbers.merge(methodName, methodSpec.lineNumbers) { lineNumbers1, lineNumbers2 ->
                        lineNumbers1 + lineNumbers2
                    }
                }

                classSpec.revision++
                val methodName2Factory = generateSpecClassAndGetMethodName2Factory(
                    className = className,
                    fileName = fileName,
                    classRevision = classSpec.revision,
                    methodName2LineNumbers = methodName2LineNumbers
                )

                methodName2LineNumbers.forEach { (methodName, lineNumbers) ->
                    classSpec.methodName2Spec[methodName] = MethodSpec(
                        lineNumbers = lineNumbers,
                        factory = methodName2Factory[methodName]!!
                    )
                }
            }
        }
    }

    private fun updateNotSynchronizedClassName2Spec() {
        while (true) {
            try {
                notSynchronizedClassName2Spec = className2Spec.asSequence()
                    .map { (className, spec) ->
                        className to NotSynchronizedClassSpec(spec.fileName, HashMap(spec.methodName2Spec))
                    }
                    .toMap(HashMap(className2Spec.size))
                return
            } catch (_: ConcurrentModificationException) { }
        }
    }

    private class ClassSpec(
        val fileName: String?
    ) {
        var revision = -1
        val methodName2Spec = ConcurrentHashMap<String, MethodSpec>()
    }

    private class MethodSpec(
        val lineNumbers: Set<Int>,
        val factory: SpecFactory
    )

    private class NotSynchronizedClassSpec(
        val fileName: String?,
        val methodName2Spec: Map<String, MethodSpec>
    )
}

internal class SpecFactoryImpl(override val handle: MethodHandle): SpecFactory {
    override fun createNotCallingNextHandle(lineNumber: Int, nextContinuation: Continuation<*>): Any =
        DecoroutinatorSpecImpl(lineNumber, nextContinuation as BaseContinuationImpl)

    override fun createCallingNextHandle(
        lineNumber: Int,
        nextHandle: MethodHandle,
        nextSpec: Any,
        nextContinuation: Continuation<*>
    ): Any =
        DecoroutinatorSpecImpl(lineNumber, nextHandle, nextSpec, nextContinuation as BaseContinuationImpl)
}
