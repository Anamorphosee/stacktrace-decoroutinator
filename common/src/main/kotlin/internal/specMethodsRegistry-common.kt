@file:Suppress("NewApi", "PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorSpec
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessor
import java.lang.invoke.MethodHandle
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

val specLineNumberMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == Int::class.javaPrimitiveType }!!
    .name
val isLastSpecMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == Boolean::class.javaPrimitiveType }!!
    .name
val nextSpecHandleMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == MethodHandle::class.java }!!
    .name
val nextSpecMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == DecoroutinatorSpec::class.java }!!
    .name
val resumeNextMethodName: String = DecoroutinatorSpec::class.java.methods
    .find { it.returnType == Object::class.java && it.parameterCount == 1 }!!
    .name

abstract class BaseSpecMethodsRegistry: SpecMethodsRegistry {
    private val classesByName: MutableMap<String, ClassSpec> = HashMap()
    private val classesByNameUpdateLock = ReentrantLock()

    private fun getClassSpec(specClassName: String): ClassSpec =
        classesByName.optimisticLockGetOrPut(
            key = specClassName,
            lock = classesByNameUpdateLock
        ) { ClassSpec() }

    override fun getSpecMethodFactory(element: StackTraceElement): SpecMethodsFactory? {
        SpecMethodsRegistryImpl.getSpecMethodFactory(element)?.let { return it }

        val classSpec = getClassSpec(element.className)

        fun getSpecMethodFactory(): SpecMethodsFactory? {
            val methodsByName = classSpec[element.fileName] ?: return null
            val methodSpec = methodsByName[element.methodName] ?: return null
            if (element.normalizedLineNumber !in methodSpec.lineNumbers) return null
            return methodSpec.factory
        }
        getSpecMethodFactory()?.let { return it }

        classSpec.updateLock.withLock {
            getSpecMethodFactory()?.let { return it }

            val lineNumbersByMethod = mutableMapOf<String, MutableSet<Int>>()
            classSpec[element.fileName]?.let { methodsByName ->
                methodsByName.forEach { (methodName, method) ->
                    lineNumbersByMethod[methodName] = method.lineNumbers.toMutableSet()
                }
            }
            lineNumbersByMethod.getOrPut(element.methodName) {
                mutableSetOf(UNKNOWN_LINE_NUMBER)
            }.add(element.normalizedLineNumber)

            classSpec.revision++
            val factoriesByMethod = generateSpecMethodFactories(
                className = element.className,
                classRevision = classSpec.revision,
                fileName = element.fileName,
                lineNumbersByMethod = lineNumbersByMethod
            ) ?: run {
                classSpec.revision--
                return null
            }
            assert { factoriesByMethod.keys == lineNumbersByMethod.keys }

            classSpec[element.fileName] =
                factoriesByMethod.mapValuesCompact(methodsNumberThreshold) { (methodName, specMethodsFactory) ->
                    MethodSpec(
                        factory = specMethodsFactory,
                        lineNumbers = lineNumbersByMethod[methodName]!!.toIntArray()
                    )
                }
        }

        return getSpecMethodFactory()!!
    }

    abstract fun generateSpecMethodFactories(
        className: String,
        classRevision: Int,
        fileName: String?,
        lineNumbersByMethod: Map<String, Set<Int>>
    ): Map<String, SpecMethodsFactory>?

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

    override fun getSpecMethodFactory(element: StackTraceElement): SpecMethodsFactory? {
        val classSpec = getClassSpec(element.className) ?: return null
        if (classSpec.fileName != element.fileName) return null
        val methodSpec = classSpec.methodsByName[element.methodName] ?: return null
        if (element.normalizedLineNumber !in methodSpec.lineNumbers) return null
        return methodSpec
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
        override fun getSpecAndMethodHandle(
            accessor: BaseContinuationAccessor,
            element: StackTraceElement,
            nextContinuation: BaseContinuation?,
            nextSpec: SpecAndMethodHandle?
        ): SpecAndMethodHandle {
            ifAssertionEnabled {
                val clazz = getClassSpec(element.className)!!
                check(clazz.fileName == element.fileName)
                check(clazz.methodsByName[element.methodName] == this)
                check(element.normalizedLineNumber in lineNumbers)
            }
            return SpecAndMethodHandle(
                specMethodHandle = specMethod,
                spec = DecoroutinatorSpecImpl(
                    accessor = accessor,
                    lineNumber = element.normalizedLineNumber,
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
