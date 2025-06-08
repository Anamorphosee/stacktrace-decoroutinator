@file:Suppress("NewApi", "PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorBaseContinuationAccessor
import java.lang.invoke.MethodHandle
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

abstract class BaseSpecMethodsRegistry: SpecMethodsRegistry {
    private val classesByName: MutableMap<String, ClassSpec> = HashMap()
    private val classesByNameUpdateLock = ReentrantLock()

    private fun getClassSpec(specClassName: String): ClassSpec =
        classesByName.optimisticLockGetOrPut(
            key = specClassName,
            lock = classesByNameUpdateLock
        ) { ClassSpec() }

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
                            classSpec[fileName] = factoriesByMethod.mapValuesCompact(methodsNumberThreshold) { (methodName, specMethodsFactory) ->
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

    private class MethodSpec(
        val factory: SpecMethodsFactory,
        val lineNumbers: IntArray
    )

    private class ClassSpec {
        var revision: Int = -1
        val methodsByFileNameAndMethodName: MutableMap<String?, Map<String, MethodSpec>> = CompactMap()
        val updateLock = ReentrantLock()

        operator fun get(fileName: String?): Map<String, MethodSpec>? =
            try {
                methodsByFileNameAndMethodName[fileName]
            } catch (_: ConcurrentModificationException) {
                updateLock.withLock {
                    methodsByFileNameAndMethodName[fileName]
                }
            }

        operator fun set(fileName: String?, methodsByMethodName: Map<String, MethodSpec>) {
            updateLock.withLock {
                methodsByFileNameAndMethodName[fileName] = methodsByMethodName
            }
        }
    }
}

internal object SpecMethodsRegistryImpl: SpecMethodsRegistry {
    private val classSpecsByName: MutableMap<String, ClassSpec> = HashMap()
    private val classSpecsByNameUpdateLock = ReentrantLock()

    private class ClassSpec(
        val fileName: String?,
        val methodsByName: Map<String, MethodSpec>
    ) {
        companion object {
            val notSet = ClassSpec(null, emptyMap())
        }
    }

    private fun getClassSpec(specClassName: String): ClassSpec? =
        classSpecsByName.optimisticLockGet(
            key = specClassName,
            lock = classSpecsByNameUpdateLock,
            notSetValue = ClassSpec.notSet
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
            accessor: DecoroutinatorBaseContinuationAccessor,
            element: StacktraceElement,
            nextContinuation: BaseContinuation,
            nextSpec: SpecAndItsMethodHandle?
        ): SpecAndItsMethodHandle {
            assert {
                val clazz = getClassSpec(element.className)!!
                assert { clazz.fileName == element.fileName }
                assert { clazz.methodsByName[element.methodName] == this }
                element.lineNumber in lineNumbers
            }
            return SpecAndItsMethodHandle(
                specMethodHandle = specMethod,
                spec = DecoroutinatorSpecImpl(
                    accessor = accessor,
                    lineNumber = element.lineNumber,
                    nextSpecAndItsMethod = nextSpec,
                    nextContinuation = nextContinuation
                )
            )
        }
    }

    private fun register(spec: TransformedClassesRegistry.TransformedClassSpec) {
        if (spec.skipSpecMethods) return
        val methodsByName = spec.lineNumbersByMethod.mapValuesCompact(methodsNumberThreshold) { (methodName, lineNumbers) ->
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
}
