@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.runtime.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import java.lang.invoke.MethodHandle
import java.util.concurrent.ConcurrentHashMap

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
                    val file = clazz.filesByName[fileName]
                    val needRebuild = file == null || elements.groupBy { it.methodName }.any { (methodName, elements) ->
                        val lineNumbers = file.lineNumbersByMethod[methodName]
                        lineNumbers == null || elements.any {
                            generatedFactories[it] = file.factory
                            it.lineNumber !in lineNumbers
                        }
                    }

                    if (needRebuild) {
                        val generatedFactory = synchronized(clazz) {
                            clazz.revision++
                            val lineNumbersByMethod = mutableMapOf<String, Set<Int>>()
                            clazz.filesByName[fileName]?.let { lineNumbersByMethod.putAll(it.lineNumbersByMethod) }
                            elements.groupBy { it.methodName }.forEach { (methodName, elements) ->
                                lineNumbersByMethod.compute(methodName) { _, lineNumbers: Set<Int>? ->
                                    buildSet {
                                        if (lineNumbers != null) addAll(lineNumbers)
                                        elements.forEach { add(it.lineNumber) }
                                    }
                                }
                            }
                            val factory = generateSpecMethodFactory(
                                className = className,
                                classRevision = clazz.revision,
                                fileName = fileName,
                                lineNumbersByMethod = lineNumbersByMethod
                            )
                            clazz.filesByName[fileName] = FileSpec(
                                factory = factory,
                                lineNumbersByMethod = lineNumbersByMethod
                            )
                            factory
                        }

                        elements.forEach { generatedFactories[it] = generatedFactory }
                    }
                }
            }

        return generatedFactories
    }

    abstract fun generateSpecMethodFactory(
        className: String,
        classRevision: Int,
        fileName: String?,
        lineNumbersByMethod: Map<String, Set<Int>>
    ): SpecMethodsFactory

    private val classesByName: MutableMap<String, ClassSpec> = ConcurrentHashMap()

    private class FileSpec(
        val factory: SpecMethodsFactory,
        val lineNumbersByMethod: Map<String, Set<Int>>
    )

    private class ClassSpec {
        var revision: Int = -1
        val filesByName: MutableMap<String?, FileSpec> = ConcurrentHashMap()
    }
}

internal object SpecMethodsRegistryImpl: SpecMethodsRegistry {
    init {
        TransformedClassesRegistry.addListener(this::register)
        TransformedClassesRegistry.transformedClasses.forEach(this::register)
    }

    override fun getSpecMethodFactoriesByStacktraceElement(
        elements: Set<StacktraceElement>
    ): Map<StacktraceElement, SpecMethodsFactory> {
        val specMethodFactoriesByElement = mutableMapOf<StacktraceElement, SpecMethodsFactory>()
        elements.groupBy { it.className }.forEach { (className, elements) ->
            val clazz = classesByName[className]
            if (clazz != null) {
                elements.asSequence()
                    .filter { it.fileName == clazz.fileName }
                    .groupBy { it.methodName }
                    .forEach { (methodName, elements) ->
                        val method = clazz.methodsByName[methodName]
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

    private val classesByName: MutableMap<String, ClassSpec> = ConcurrentHashMap()

    private class MethodSpec(
        val lineNumbers: IntArray,
        val specMethod: MethodHandle
    ): SpecMethodsFactory {
        override fun getSpecAndItsMethodHandle(
            element: StacktraceElement,
            nextContinuation: BaseContinuation,
            nextSpec: SpecAndItsMethodHandle?
        ): SpecAndItsMethodHandle {
            assert {
                val clazz = classesByName[element.className]!!
                assert { clazz.fileName == element.fileName }
                assert { clazz.methodsByName[element.methodName] == this }
                element.lineNumber in lineNumbers
            }
            return SpecAndItsMethodHandle(
                specMethodHandle = specMethod,
                spec = DecoroutinatorSpecImpl(
                    lineNumber = element.lineNumber,
                    nextSpecAndItsMethod = nextSpec,
                    nextContinuation = nextContinuation,
                )
            )
        }
    }

    private class ClassSpec(
        val fileName: String?,
        val methodsByName: Map<String, MethodSpec>
    )

    private fun register(className: String, spec: TransformedClassesRegistry.TransformedClassSpec) {
        val clazz = Class.forName(className)
        val methodsByName = spec.lineNumbersByMethod.mapValues { (methodName, lineNumbers) ->
            val specMethod = spec.lookup.findStatic(clazz, methodName, specMethodType)
            MethodSpec(
                lineNumbers = lineNumbers,
                specMethod = specMethod
            )
        }
        val classSpec = ClassSpec(
            fileName = spec.fileName,
            methodsByName = methodsByName
        )
        classesByName[className] = classSpec
    }
}
