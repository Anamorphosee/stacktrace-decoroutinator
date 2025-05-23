@file:Suppress("NewApi", "PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import java.lang.invoke.MethodHandle
import java.util.concurrent.ConcurrentHashMap
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
                val clazz = classesByName.computeIfAbsent(className) { _ -> ClassSpec() }
                elements.groupBy { it.fileName }.forEach { (fileName, elements) ->
                    val methodsByName = clazz[fileName]
                    val needRebuild = methodsByName == null || elements.groupBy { it.methodName }.any { (methodName, elements) ->
                        val method = methodsByName[methodName]
                        method == null || elements.any {
                            if (it.lineNumber in method.lineNumbers) {
                                generatedFactories[it] = method.factory
                                false
                            } else {
                                true
                            }
                        }
                    }

                    if (needRebuild) {
                        val factoriesByMethod = synchronized(clazz) {
                            clazz.revision++
                            val lineNumbersByMethod = mutableMapOf<String, Set<Int>>()
                            clazz[fileName]?.let { methodsByName ->
                                methodsByName.forEach { (methodName, method) ->
                                    lineNumbersByMethod[methodName] = method.lineNumbers
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
                            val factoriesByMethod = generateSpecMethodFactories(
                                className = className,
                                classRevision = clazz.revision,
                                fileName = fileName,
                                lineNumbersByMethod = lineNumbersByMethod
                            )
                            clazz[fileName] = factoriesByMethod.mapValues { (methodName, factory) ->
                                MethodSpec(
                                    factory = factory,
                                    lineNumbers = lineNumbersByMethod[methodName]!!
                                )
                            }
                            factoriesByMethod
                        }

                        elements.forEach { element ->
                            factoriesByMethod[element.methodName]?.let {
                                generatedFactories[element] = it
                            }
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

    private val classesByName: MutableMap<String, ClassSpec> = ConcurrentHashMap()

    private class MethodSpec(
        val factory: SpecMethodsFactory,
        val lineNumbers: Set<Int>
    )

    private class ClassSpec {
        var revision: Int = -1
        private val methodsByFileNameAndMethodName: MutableMap<String, Map<String, MethodSpec>> = ConcurrentHashMap()
        @Volatile private var methodsByMethodNameForUnknownFileName: Map<String, MethodSpec>? = null

        operator fun get(fileName: String?): Map<String, MethodSpec>? =
            if (fileName == null) methodsByMethodNameForUnknownFileName else methodsByFileNameAndMethodName[fileName]

        operator fun set(fileName: String?, methodsByMethodName: Map<String, MethodSpec>) {
            if (fileName == null) {
                methodsByMethodNameForUnknownFileName = methodsByMethodName
            } else {
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
        TransformedClassesRegistry.transformedClasses.forEach(::register)
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
                val clazz = classSpecsByName[element.className]!!
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
