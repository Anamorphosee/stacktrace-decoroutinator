package dev.reformator.stacktracedecoroutinator.jvmagentcommon

import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorStacktraceElement
import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorStacktraceMethodHandleRegistry
import dev.reformator.stacktracedecoroutinator.common.invokeStacktraceMethodType
import java.lang.invoke.MethodHandle
import java.util.concurrent.ConcurrentHashMap

object DecoroutinatorJvmAgentStacktraceMethodHandleRegistry: DecoroutinatorStacktraceMethodHandleRegistry {
    private val className2Spec = ConcurrentHashMap<String, ClassSpec>()

    @Volatile
    private var notSynchronizedClassName2Spec = emptyMap<String, ClassSpec>()

    override fun getStacktraceMethodHandles(
        elements: Collection<DecoroutinatorStacktraceElement>
    ): Map<DecoroutinatorStacktraceElement, MethodHandle> {
        val className2Spec = notSynchronizedClassName2Spec
        var needUpdateClassName2Spec = false
        val result = mutableMapOf<DecoroutinatorStacktraceElement, MethodHandle>()
        elements.groupBy { it.className }.forEach { (className, elements) ->
            val classSpec = className2Spec[className] ?: run {
                needUpdateClassName2Spec = true
                calculateClassSpec(className)
            }
            val fileName = elements.asSequence()
                .map { it.fileName }
                .distinct()
                .single()
            if (fileName != classSpec.fileName) {
                error("different file names for class [$className]: [$fileName] and [${classSpec.fileName}]")
            }
            elements.groupBy { it.methodName }.forEach { (methodName, elements) ->
                val methodSpec = classSpec.methodName2Spec[methodName]
                    ?: error("not found stacktrace method [$methodName] for class [$className]")
                elements.forEach { element ->
                    if (element.lineNumber !in methodSpec.lineNumbers) {
                        error("not found line number [${element.lineNumber}] for stacktrace method [$methodName] " +
                                "in class [$className]")
                    }
                    result[element] = methodSpec.handle
                }
            }
        }
        if (needUpdateClassName2Spec) {
            updateClassName2Spec()
        }
        return result
    }

    private fun calculateClassSpec(className: String): ClassSpec = className2Spec.computeIfAbsent(className) {
        val clazz = Class.forName(className)
        val marker: DecoroutinatorAgentTransformedMarker =
            clazz.getAnnotation(DecoroutinatorAgentTransformedMarker::class.java)
                ?: if (decoroutinatorJvmAgentRegistry.isRetransformationAllowed) {
                    decoroutinatorJvmAgentRegistry.retransform(clazz)
                    clazz.getAnnotation(DecoroutinatorAgentTransformedMarker::class.java)
                } else {
                    null
                } ?: error("The class [$className] was not transformed for Stacktrace-decoroutinator.")
        val methodName2Spec = buildMap(marker.methodNames.size) {
            var lineNumberIndex = 0
            marker.methodNames.forEachIndexed { index, methodName ->
                val lineNumbersCount = marker.lineNumbersCounts[index]
                val lineNumbers = buildSet(lineNumbersCount) {
                    repeat(lineNumbersCount) {
                        add(marker.lineNumbers[lineNumberIndex++])
                    }
                }
                val lookup = decoroutinatorJvmAgentRegistry.getLookup(clazz)
                val handle: MethodHandle = lookup.findStatic(clazz, methodName, invokeStacktraceMethodType)
                this[methodName] = MethodSpec(lineNumbers, handle)
            }
        }
        val fileName = if (marker.fileNamePresent) marker.fileName else null
        ClassSpec(fileName, methodName2Spec)
    }

    private fun updateClassName2Spec() {
        while (true) {
            try {
                notSynchronizedClassName2Spec = HashMap(className2Spec)
                return
            } catch (_: ConcurrentModificationException) { }
        }
    }
}

private data class ClassSpec(
    val fileName: String?,
    val methodName2Spec: Map<String, MethodSpec>
)

private data class MethodSpec(
    val lineNumbers: Set<Int>,
    val handle: MethodHandle
)
