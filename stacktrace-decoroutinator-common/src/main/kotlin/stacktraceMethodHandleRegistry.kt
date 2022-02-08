package dev.reformator.stacktracedecoroutinator.registry

import dev.reformator.stacktracedecoroutinator.DecoroutinatorStacktraceElement
import dev.reformator.stacktracedecoroutinator.utils.classLoader
import dev.reformator.stacktracedecoroutinator.utils.invokeStacktraceMethodType
import dev.reformator.stacktracedecoroutinator.utils.lookup
import java.lang.invoke.MethodHandle
import java.util.concurrent.ConcurrentHashMap

interface DecoroutinatorStacktraceMethodHandleRegistry {
    fun getStacktraceMethodHandles(elements: Collection<DecoroutinatorStacktraceElement>):
            Map<DecoroutinatorStacktraceElement, MethodHandle>
}

abstract class BaseDecoroutinatorStacktraceMethodHandleRegistry: DecoroutinatorStacktraceMethodHandleRegistry {
    private val className2Spec = ConcurrentHashMap<String, ClassSpec>()
    @Volatile
    private var notSynchronizedClassName2Spec = emptyMap<String, NotSynchronizedClassSpec>()

    override fun getStacktraceMethodHandles(elements: Collection<DecoroutinatorStacktraceElement>):
            Map<DecoroutinatorStacktraceElement, MethodHandle> {
        val result = mutableMapOf<DecoroutinatorStacktraceElement, MethodHandle>()

        val missingElements = getMissingElementsAndFillResult(elements, result)

        if (missingElements.isNotEmpty()) {
            regenerateClassesForMissingElements(missingElements)
            updateNotSynchronizedClassName2Spec()

            missingElements.groupBy { it.className }.forEach { (className, missingElements) ->
                val classSpec = className2Spec[className]!!
                missingElements.groupBy { it.methodName }.forEach { (methodName, missingElements) ->
                    val methodSpec = classSpec.methodName2Spec[methodName]!!
                    missingElements.forEach {
                        result[it] = methodSpec.handle
                    }
                }
            }
        }

        return result
    }

    protected abstract fun generateStacktraceClass(
        className: String,
        fileName: String?,
        classRevision: Int,
        methodName2LineNumbers: Map<String, Set<Int>>
    ): Class<*>

    private fun getMissingElementsAndFillResult(
        elements: Collection<DecoroutinatorStacktraceElement>,
        result: MutableMap<DecoroutinatorStacktraceElement, MethodHandle>
    ) = buildSet {
        elements.groupBy { it.className }.forEach classForEach@{ (className, elements) ->
            val classSpec = notSynchronizedClassName2Spec[className]
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
                        result[it] = methodSpec.handle
                    } else {
                        add(it)
                    }
                }
            }
        }
    }

    private fun regenerateClassesForMissingElements(
        missingElements: Collection<DecoroutinatorStacktraceElement>
    ) = missingElements.groupBy { it.className }.forEach { (className, missingElements) ->
        val fileName = missingElements.asSequence()
            .map { it.fileName }
            .distinct()
            .single()

        val classSpec = className2Spec.computeIfAbsent(className) { ClassSpec(fileName) }
        if (classSpec.fileName != fileName) {
            throw IllegalStateException("different file names for class [$className]: [${classSpec.fileName}] and [$fileName]")
        }

        synchronized(classSpec) {
            val methodName2LineNumbers = mutableMapOf<String, Set<Int>>()

            val needToRegenerateClass = run {
                var needToRegenerateClass = false

                missingElements.groupBy { it.methodName }.forEach classForEach@{ (methodName, missingElements) ->
                    val methodSpec = classSpec.methodName2Spec[methodName]
                    if (methodSpec == null) {
                        methodName2LineNumbers[methodName] = missingElements.asSequence()
                            .map { it.lineNumber }
                            .toSet()
                        needToRegenerateClass = true
                        return@classForEach
                    }

                    val missingLineNumbers = missingElements.asSequence()
                        .map { it.lineNumber }
                        .filter { it !in methodSpec.lineNumbers }
                        .toSet()
                    if (missingLineNumbers.isNotEmpty()) {
                        methodName2LineNumbers[methodName] = missingLineNumbers
                        needToRegenerateClass = true
                    }
                }

                needToRegenerateClass
            }

            if (needToRegenerateClass) {
                classSpec.methodName2Spec.forEach { (methodName, methodSpec) ->
                    methodName2LineNumbers.merge(methodName, methodSpec.lineNumbers) { lineNumbers1, lineNumbers2 ->
                        lineNumbers1 + lineNumbers2
                    }
                }

                classSpec.revision++
                val clazz = generateStacktraceClass(
                    className = className,
                    fileName = fileName,
                    classRevision = classSpec.revision,
                    methodName2LineNumbers = methodName2LineNumbers
                )

                methodName2LineNumbers.forEach { (methodName, lineNumbers) ->
                    val handle = lookup.findStatic(clazz, methodName, invokeStacktraceMethodType)
                    classSpec.methodName2Spec[methodName] = MethodSpec(
                        lineNumbers = lineNumbers,
                        handle = handle
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
            } catch (e: ConcurrentModificationException) { }
        }
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
    val handle: MethodHandle
)

private class NotSynchronizedClassSpec(
    val fileName: String?,
    val methodName2Spec: Map<String, MethodSpec>
)
