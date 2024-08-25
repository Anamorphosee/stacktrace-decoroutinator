@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.generator.internal

import dev.reformator.stacktracedecoroutinator.common.internal.BaseSpecMethodsRegistry
import dev.reformator.stacktracedecoroutinator.common.internal.SpecAndItsMethodHandle
import dev.reformator.stacktracedecoroutinator.common.internal.SpecMethodsFactory
import dev.reformator.stacktracedecoroutinator.common.internal.assert
import java.util.concurrent.CopyOnWriteArrayList

internal class GeneratorSpecMethodsRegistry: BaseSpecMethodsRegistry() {
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
            SpecMethodsFactory { element, nextContinuation, nextSpec ->
                assert { element.className == className }
                assert { element.fileName == fileName }
                assert { element.methodName == methodName }
                assert { element.lineNumber in lineNumbersByMethod[element.methodName]!! }
                val spec = loader.getSpec(
                    lineNumber = element.lineNumber,
                    nextContinuation = nextContinuation,
                    nextSpec = nextSpec
                )
                SpecAndItsMethodHandle(
                    specMethodHandle = handle,
                    spec = spec
                )
            }
        }
    }

    private val loadersByRevision: MutableList<DecoroutinatorClassLoader> = CopyOnWriteArrayList()
}