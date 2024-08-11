@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.generator

import dev.reformator.stacktracedecoroutinator.runtime.internal.BaseSpecRegistry
import dev.reformator.stacktracedecoroutinator.runtime.internal.SpecFactory
import java.lang.invoke.MethodHandle
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.Continuation

class GeneratorSpecRegistry: BaseSpecRegistry() {
    private val classLoaderByRevision: MutableList<DecoroutinatorClassLoader> = CopyOnWriteArrayList()

    override fun generateSpecClassAndGetMethodName2Factory(
        className: String,
        fileName: String?,
        classRevision: Int,
        methodName2LineNumbers: Map<String, Set<Int>>
    ): Map<String, SpecFactory> {
        while (classLoaderByRevision.size <= classRevision) {
            classLoaderByRevision.add(DecoroutinatorClassLoader())
        }
        val loader = classLoaderByRevision[classRevision]
        val clazz = loader.generateClass(className, fileName, methodName2LineNumbers)
        return methodName2LineNumbers.mapValues { (methodName, _) ->
            val specMethodHandle = loader.getSpecMethodHandle(clazz, methodName)
            object: SpecFactory {
                override fun createNotCallingNextHandle(lineNumber: Int, nextContinuation: Continuation<*>): Any =
                    loader.createSpecNotCallingNextHandle(lineNumber, nextContinuation)

                override fun createCallingNextHandle(
                    lineNumber: Int,
                    nextHandle: MethodHandle,
                    nextSpec: Any,
                    nextContinuation: Continuation<*>
                ): Any =
                    loader.createSpecCallingNextHandle(lineNumber, nextHandle, nextSpec, nextContinuation)

                override val handle: MethodHandle
                    get() = specMethodHandle
            }
        }
    }
}
