@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.runtime.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorTransformed
import java.lang.invoke.MethodHandles
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

internal object TransformedClassesRegistry {
    class TransformedClassSpec(
        val fileName: String?,
        val lookup: MethodHandles.Lookup,
        val lineNumbersByMethod: Map<String, IntArray>,
        val baseContinuationClasses: Set<Class<out BaseContinuation>>
    )

    fun interface Listener {
        fun onNewTransformedClass(className: String, spec: TransformedClassSpec)
        fun onException(exception: Throwable) { }
    }

    val transformedClasses: Map<String, TransformedClassSpec>
        get() {
            while (true) {
                try {
                    return HashMap(_transformedClasses)
                } catch (_: ConcurrentModificationException) { }
            }
        }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun registerTransformedClass(lookup: MethodHandles.Lookup) {
        val clazz: Class<*> = lookup.lookupClass()
        val meta: DecoroutinatorTransformed? = clazz.getDeclaredAnnotation(DecoroutinatorTransformed::class.java)
        if (meta != null && meta.version <= TRANSFORMED_VERSION) {
            val transformedClassSpec = run {
                val lineNumbersByMethod = run {
                    val lineNumberIterator = meta.lineNumbers.iterator()
                    meta.methodNames.asSequence()
                        .mapIndexed { index, methodName ->
                            val lineNumbers = IntArray(meta.lineNumbersCounts[index]) { lineNumberIterator.nextInt() }
                            methodName to lineNumbers
                        }
                        .toMap()
                }
                val fileName = if (meta.fileNamePresent) meta.fileName else null
                val baseContinuationClasses = meta.baseContinuationClasses.asSequence()
                    .filter { BaseContinuation::class.java.isAssignableFrom(it.java) }
                    .map {
                        @Suppress("UNCHECKED_CAST")
                        it.java as Class<out BaseContinuation>
                    }
                    .toSet()
                TransformedClassSpec(
                    fileName = fileName,
                    lookup = lookup,
                    lineNumbersByMethod = lineNumbersByMethod,
                    baseContinuationClasses = baseContinuationClasses
                )
            }
            _transformedClasses[clazz.name] = transformedClassSpec
            callListeners(clazz.name, transformedClassSpec)
        } else if (meta != null) {
            error("Class [$clazz] has transformed meta of version [${meta.version}]. Please update Decoroutinator")
        }
    }

    private val _transformedClasses: MutableMap<String, TransformedClassSpec> = ConcurrentHashMap()
    private val listeners: MutableList<Listener> = CopyOnWriteArrayList()

    private fun callListeners(className: String, spec: TransformedClassSpec) {
        listeners.forEach {
            try {
                it.onNewTransformedClass(className, spec)
            } catch (exception: Throwable) {
                try {
                    it.onException(exception)
                } catch (_: Throwable) {}
            }
        }
    }
}
