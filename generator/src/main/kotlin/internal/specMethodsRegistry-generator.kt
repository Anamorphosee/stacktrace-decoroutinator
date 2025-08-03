@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.generator.internal

import dev.reformator.stacktracedecoroutinator.common.internal.*
import java.util.concurrent.CopyOnWriteArrayList

internal class GeneratorSpecMethodsRegistry: BaseSpecMethodsRegistry() {
    init {
        //assert the platform
        DecoroutinatorClassLoader()
    }

    override fun generateSpecMethodFactories(
        className: String,
        classRevision: Int,
        fileName: String?,
        lineNumbersByMethod: Map<String, Set<Int>>
    ): Map<String, SpecMethodsFactory> {
        while (loadersByRevision.size <= classRevision) loadersByRevision.add(DecoroutinatorClassLoader())
        val loader = loadersByRevision[classRevision]
        val specHandlesByMethod = loader.buildClassAndGetSpecHandlesByMethod(
            className = className,
            fileName = fileName,
            lineNumbersByMethod = lineNumbersByMethod
        )
        return specHandlesByMethod.mapValues { (methodName, handle) ->
            SpecMethodsFactory { accessor, element, nextContinuation, nextSpec ->
                assert { element.className == className }
                assert { element.fileName == fileName }
                assert { element.methodName == methodName }
                assert { element.normalizedLineNumber in lineNumbersByMethod[element.methodName]!! }
                val spec = DecoroutinatorSpecImpl(
                    accessor = accessor,
                    lineNumber = element.normalizedLineNumber,
                    nextSpecAndItsMethod = nextSpec,
                    nextContinuation = nextContinuation
                )
                SpecAndMethodHandle(
                    specMethodHandle = handle,
                    spec = spec
                )
            }
        }
    }

    private val loadersByRevision: MutableList<DecoroutinatorClassLoader> = CopyOnWriteArrayList()
}