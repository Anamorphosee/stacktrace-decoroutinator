@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.runtime.internal

import dev.reformator.stacktracedecoroutinator.runtime.DecoroutinatorTransformed
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.jvm.internal.BaseContinuationImpl
import kotlin.coroutines.jvm.internal.DecoroutinatorSpecImpl

internal object TransformedClassesSpecRegistry: SpecRegistry {
    private val className2Spec: MutableMap<String, ClassSpec> = ConcurrentHashMap()

    @Volatile
    private var notSynchronizedClassName2Spec = emptyMap<String, ClassSpec>()

    override fun getSpecFactories(
        elements: Collection<StacktraceElement>
    ): Map<StacktraceElement, SpecFactory> =
        buildMap {
            elements.groupBy { it.className }.forEach { (className, elements) ->
                val classSpec = notSynchronizedClassName2Spec[className]
                if (classSpec != null) {
                    elements.asSequence()
                        .filter { it.fileName == classSpec.fileName }
                        .groupBy { it.methodName }
                        .forEach { (methodName, elements) ->
                            val methodSpec = classSpec.methodName2Spec[methodName]
                            if (methodSpec != null) {
                                elements.forEach {
                                    if (it.lineNumber in methodSpec.lineNumbers) {
                                        put(it, methodSpec)
                                    }
                                }
                            }
                        }
                }
            }
        }

    internal fun registerClass(lookup: MethodHandles.Lookup) {
        val clazz: Class<*> = lookup.lookupClass()
        val meta: DecoroutinatorTransformed? = clazz.getDeclaredAnnotation(DecoroutinatorTransformed::class.java)
        if (meta != null && meta.version == TRANSFORMED_VERSION) {
            val lineNumberIterator = meta.lineNumbers.iterator()
            val classSpec = ClassSpec(
                fileName = if (meta.fileNamePresent) meta.fileName else null,
                methodName2Spec = meta.methodNames.asSequence()
                    .mapIndexed { methodIndex, methodName ->
                        val methodSpec = MethodSpec(
                            lineNumbers = buildSet {
                                repeat(meta.lineNumbersCounts[methodIndex]) {
                                    add(lineNumberIterator.next())
                                }
                            },
                            handle = lookup.findStatic(clazz, methodName, specMethodType)
                        )
                        methodName to methodSpec
                    }
                    .toMap()
            )
            className2Spec[clazz.name] = classSpec
            while (true) {
                try {
                    notSynchronizedClassName2Spec = HashMap(className2Spec)
                    return
                } catch (_: ConcurrentModificationException) { }
            }
        } else if (meta != null && meta.version > TRANSFORMED_VERSION) {
            error("Class [$clazz] has transformed meta of version [${meta.version}]. Please update Decoroutinator")
        }
    }

    private data class ClassSpec(
        val fileName: String?,
        val methodName2Spec: Map<String, MethodSpec>
    )

    private data class MethodSpec(
        val lineNumbers: Set<Int>,
        override val handle: MethodHandle
    ): SpecFactory {
        override fun createNotCallingNextHandle(lineNumber: Int, nextContinuation: Continuation<*>): Any =
            DecoroutinatorSpecImpl(lineNumber, nextContinuation as BaseContinuationImpl)

        override fun createCallingNextHandle(
            lineNumber: Int,
            nextHandle: MethodHandle,
            nextSpec: Any,
            nextContinuation: Continuation<*>
        ): Any =
            DecoroutinatorSpecImpl(lineNumber, nextHandle, nextSpec, nextContinuation as BaseContinuationImpl)
    }
}
