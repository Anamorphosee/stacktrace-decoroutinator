package dev.reformator.stacktracedecoroutinator.jvmagentcommon

import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorStacktraceElement
import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorStacktraceMethodHandleRegistry
import dev.reformator.stacktracedecoroutinator.common.invokeStacktraceMethodType
import dev.reformator.stacktracedecoroutinator.jvmlegacycommon.DecoroutinatorJvmLegacyStacktraceMethodHandleRegistry
import java.lang.invoke.MethodHandle
import java.util.concurrent.ConcurrentHashMap

object DecorountinatorJvmStacktraceMethodHandleRegistry: DecoroutinatorStacktraceMethodHandleRegistry {
    private val className2Spec = ConcurrentHashMap<String, ClassSpec>()

    @Volatile
    private var notSynchronizedClassName2Spec = emptyMap<String, ClassSpec>()

    override fun getStacktraceMethodHandles(
        elements: Collection<DecoroutinatorStacktraceElement>
    ): Map<DecoroutinatorStacktraceElement, MethodHandle> = buildMap {
        elements.groupBy { it.className }.forEach { (className, elements) ->
            val clazz = Class.forName(className)
            if (tryAgentMethodHandleRegistry(className, clazz, elements)) {
                return@forEach
            }
            if (decoroutinatorJvmAgentRegistry.isRetransformationAllowed) {
                decoroutinatorJvmAgentRegistry.retransform(clazz)
                if (tryAgentMethodHandleRegistry(className, clazz, elements)) {
                    return@forEach
                }
            }
            putAll(DecoroutinatorJvmLegacyStacktraceMethodHandleRegistry.getStacktraceMethodHandles(elements))
        }
    }

    private fun calculateClassSpec(className: String, clazz: Class<*>): ClassSpec = className2Spec.computeIfAbsent(className) {
        val marker: DecoroutinatorAgentTransformedMarker =
            clazz.getAnnotation(DecoroutinatorAgentTransformedMarker::class.java)
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

    private fun MutableMap<DecoroutinatorStacktraceElement, MethodHandle>.tryAgentMethodHandleRegistry(
        className: String,
        clazz: Class<*>,
        elements: List<DecoroutinatorStacktraceElement>
    ): Boolean =
        if (clazz.isDecoroutinatorAgentTransformed) {
            val className2Spec = notSynchronizedClassName2Spec
            var needUpdateClassName2Spec = false
            val classSpec = className2Spec[className] ?: run {
                needUpdateClassName2Spec = true
                calculateClassSpec(className, clazz)
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
                        error(
                            "not found line number [${element.lineNumber}] for stacktrace method [$methodName] " +
                                    "in class [$className]"
                        )
                    }
                    this[element] = methodSpec.handle
                }
            }
            if (needUpdateClassName2Spec) {
                updateClassName2Spec()
            }
            true
        } else {
            false
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
