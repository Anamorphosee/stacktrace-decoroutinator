@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.runtime

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.util.concurrent.ConcurrentHashMap

@Target(AnnotationTarget.CLASS)
@Retention
annotation class DecoroutinatorTransformed(
    val fileNamePresent: Boolean = true,
    val fileName: String = "",
    val methodNames: Array<String>,
    val lineNumbersCounts: IntArray,
    val lineNumbers: IntArray
)

val Class<*>.isDecoroutinatorTransformed: Boolean
    get() = isAnnotationPresent(DecoroutinatorTransformed::class.java)

internal object TransformedClassMethodHandleRegistry: MethodHandleRegistry {
    private val className2Spec: MutableMap<String, ClassSpec> = ConcurrentHashMap()

    @Volatile
    private var notSynchronizedClassName2Spec = emptyMap<String, ClassSpec>()

    override fun getStacktraceMethodHandles(
        elements: Collection<StacktraceElement>
    ): Map<StacktraceElement, MethodHandle> =
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
                                        put(it, methodSpec.handle)
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
        if (meta != null) {
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
                            handle = lookup.findStatic(clazz, methodName, invokeStacktraceMethodType)
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
}

@FunctionMarker
@Suppress("unused")
fun registerClass(lookup: MethodHandles.Lookup) {
    TransformedClassMethodHandleRegistry.registerClass(lookup)
}

val registerTransformedFunctionClass = getFileClass()
val registerTransformedFunctionName = registerTransformedFunctionClass.markedFunctionName
