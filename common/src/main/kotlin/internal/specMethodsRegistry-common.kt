@file:Suppress("NewApi", "PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import java.lang.invoke.MethodHandle
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

abstract class BaseSpecMethodsRegistry: SpecMethodsRegistry {
    override fun getSpecMethodFactoriesByStacktraceElement(
        elements: Set<StacktraceElement>
    ): Map<StacktraceElement, SpecMethodsFactory> {
        val transformedFactories = SpecMethodsRegistryImpl.getSpecMethodFactoriesByStacktraceElement(elements)
        if (elements.all { it in transformedFactories }) return transformedFactories

        val generatedFactories = mutableMapOf<StacktraceElement, SpecMethodsFactory>()
        generatedFactories.putAll(transformedFactories)

        elements.asSequence()
            .filter { it !in transformedFactories }
            .groupBy { it.className }
            .forEach { (className, elements) ->
                val classSpec = getClassSpec(className)
                elements.groupBy { it.fileName }.forEach { (fileName, elements) ->
                    fun isRebuildNeeded(): Boolean {
                        val methodsByName = classSpec[fileName] ?: return true
                        var result = false
                        elements.groupBy { it.methodName }.forEach fr@{ (methodName, elements) ->
                            val method = methodsByName[methodName]
                            if (method == null) {
                                result = true
                                return@fr
                            }
                            elements.forEach {
                                if (it.lineNumber in method.lineNumbers) {
                                    generatedFactories[it] = method.factory
                                } else {
                                    result = true
                                }
                            }
                        }
                        return result
                    }

                    if (isRebuildNeeded()) {
                        classSpec.updateLock.withLock {
                            if (!isRebuildNeeded()) return@withLock
                            val lineNumbersByMethod = mutableMapOf<String, Set<Int>>()
                            classSpec[fileName]?.let { methodsByName ->
                                methodsByName.forEach { (methodName, method) ->
                                    lineNumbersByMethod[methodName] = method.lineNumbers.toSet()
                                }
                            }
                            elements.groupBy { it.methodName }.forEach { (methodName, elements) ->
                                lineNumbersByMethod.compute(methodName) { _, lineNumbers: Set<Int>? ->
                                    buildSet {
                                        if (lineNumbers != null) addAll(lineNumbers)
                                        elements.forEach { add(it.lineNumber) }
                                    }
                                }
                            }
                            classSpec.revision++
                            val factoriesByMethod = generateSpecMethodFactories(
                                className = className,
                                classRevision = classSpec.revision,
                                fileName = fileName,
                                lineNumbersByMethod = lineNumbersByMethod
                            )
                            classSpec[fileName] = factoriesByMethod.mapValues { (methodName, specMethodsFactory) ->
                                MethodSpec(
                                    factory = specMethodsFactory,
                                    lineNumbers = lineNumbersByMethod[methodName]!!.toIntArray()
                                )
                            }
                            isRebuildNeeded()
                        }
                    }
                }
            }

        return generatedFactories
    }

    abstract fun generateSpecMethodFactories(
        className: String,
        classRevision: Int,
        fileName: String?,
        lineNumbersByMethod: Map<String, Set<Int>>
    ): Map<String, SpecMethodsFactory>

    private val classesByName: MutableMap<String, ClassSpec> = HashMap()
    private val classesByNameUpdateLock = ReentrantLock()

    private fun getClassSpec(specClassName: String): ClassSpec =
        try {
            classesByName[specClassName]
        } catch (_: ConcurrentModificationException) {
            null
        } ?: classesByNameUpdateLock.withLock {
            classesByName[specClassName]?.let { return it }
            val result = ClassSpec()
            classesByName[specClassName] = result
            result
        }

    private class MethodSpec(
        val factory: SpecMethodsFactory,
        val lineNumbers: IntArray
    )

    private class ClassSpec {
        var revision: Int = -1
        val methodsByFileNameAndMethodName: MutableMap<String, Map<String, MethodSpec>> = HashMap()
        var methodsByMethodNameForUnknownFileName: Map<String, MethodSpec>? = null
        val updateLock = ReentrantLock()

        operator fun get(fileName: String?): Map<String, MethodSpec>? =
            try {
                unsafeGet(fileName)
            } catch (_: ConcurrentModificationException) {
                updateLock.withLock {
                    unsafeGet(fileName)
                }
            }

        operator fun set(fileName: String?, methodsByMethodName: Map<String, MethodSpec>) {
            updateLock.withLock {
                if (fileName == null) {
                    methodsByMethodNameForUnknownFileName = methodsByMethodName
                } else {
                    methodsByFileNameAndMethodName[fileName] = methodsByMethodName
                }
            }
        }

        private fun unsafeGet(fileName: String?): Map<String, MethodSpec>? =
            if (fileName == null) {
                methodsByMethodNameForUnknownFileName
            } else {
                methodsByFileNameAndMethodName[fileName]
            }
    }
}

internal object SpecMethodsRegistryImpl: SpecMethodsRegistry {
    private val classSpecsByName: MutableMap<String, ClassSpec> = HashMap()
    private val classSpecsByNameUpdateLock = ReentrantLock()

    private class ClassSpec(
        val fileName: String?,
        val methodsByName: Map<String, MethodSpec>
    )

    override fun getSpecMethodFactoriesByStacktraceElement(
        elements: Set<StacktraceElement>
    ): Map<StacktraceElement, SpecMethodsFactory> {
        val specMethodFactoriesByElement = mutableMapOf<StacktraceElement, SpecMethodsFactory>()
        elements.groupBy { it.className }.forEach { (className, elements) ->
            val classSpec = getClassSpec(className)
            if (classSpec != null) {
                elements.asSequence()
                    .filter { it.fileName == classSpec.fileName }
                    .groupBy { it.methodName }
                    .forEach { (methodName, elements) ->
                        val method = classSpec.methodsByName[methodName]
                        if (method != null) {
                            elements.asSequence()
                                .filter { it.lineNumber in method.lineNumbers }
                                .forEach { specMethodFactoriesByElement[it] = method }
                        }
                    }
            }
        }
        return specMethodFactoriesByElement
    }

    init {
        TransformedClassesRegistry.addListener(::register)
        classSpecsByNameUpdateLock.withLock {
            TransformedClassesRegistry.transformedClasses.forEach(::register)
        }
    }

    private class MethodSpec(
        val lineNumbers: IntArray,
        val specMethod: MethodHandle
    ): SpecMethodsFactory {
        override fun getSpecAndItsMethodHandle(
            cookie: Cookie,
            element: StacktraceElement,
            nextContinuation: BaseContinuation,
            nextSpec: SpecAndItsMethodHandle?
        ): SpecAndItsMethodHandle {
            assert {
                val clazz = getClassSpec(element.className) ?: classSpecsByNameUpdateLock.withLock {
                    getClassSpec(element.className)!!
                }
                assert { clazz.fileName == element.fileName }
                assert { clazz.methodsByName[element.methodName] == this }
                element.lineNumber in lineNumbers
            }
            return SpecAndItsMethodHandle(
                specMethodHandle = specMethod,
                spec = methodHandleInvoker.createSpec(
                    cookie = cookie,
                    lineNumber = element.lineNumber,
                    nextSpecAndItsMethod = nextSpec,
                    nextContinuation = nextContinuation
                )
            )
        }
    }

    private fun register(spec: TransformedClassesRegistry.TransformedClassSpec) {
        if (spec.skipSpecMethods) return
        val methodsByName = spec.lineNumbersByMethod.mapValues { (methodName, lineNumbers) ->
            val specMethod = spec.lookup.findStatic(spec.transformedClass, methodName, specMethodType)
            MethodSpec(
                lineNumbers = lineNumbers,
                specMethod = specMethod
            )
        }
        val classSpec = ClassSpec(
            fileName = spec.fileName,
            methodsByName = methodsByName
        )
        classSpecsByNameUpdateLock.withLock {
            classSpecsByName[spec.transformedClass.name] = classSpec
        }
    }

    private fun getClassSpec(specClassName: String): ClassSpec? =
        try {
            classSpecsByName[specClassName]
        } catch (_: ConcurrentModificationException) {
            classSpecsByNameUpdateLock.withLock {
                classSpecsByName[specClassName]
            }
        }
}
