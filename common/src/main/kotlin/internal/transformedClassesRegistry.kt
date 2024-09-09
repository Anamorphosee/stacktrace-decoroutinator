@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorTransformed
import java.lang.invoke.MethodHandles
import java.lang.reflect.GenericSignatureFormatError
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

internal object TransformedClassesRegistry {
    class TransformedClassSpec(
        val transformedClass: Class<*>,
        val fileName: String?,
        val lookup: MethodHandles.Lookup,
        val lineNumbersByMethod: Map<String, IntArray>,
        val baseContinuationClasses: Set<Class<out BaseContinuation>>
    )

    fun interface Listener {
        fun onNewTransformedClass(spec: TransformedClassSpec)
        fun onException(exception: Throwable) { }
    }

    val transformedClasses: Collection<TransformedClassSpec>
        get() {
            while (true) {
                try {
                    return ArrayList(_transformedClasses.values)
                } catch (_: ConcurrentModificationException) { }
            }
        }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun registerTransformedClass(lookup: MethodHandles.Lookup) {
        val clazz: Class<*> = lookup.lookupClass()
        val meta = try {
            clazz.getDeclaredAnnotation(DecoroutinatorTransformed::class.java)?.let { transformedAnnotation ->
                parseTransformationMetadata(
                    fileNamePresent = transformedAnnotation.fileNamePresent,
                    fileName = transformedAnnotation.fileName,
                    methodNames = transformedAnnotation.methodNames.toList(),
                    lineNumbersCounts = transformedAnnotation.lineNumbersCounts.toList(),
                    lineNumbers = transformedAnnotation.lineNumbers.toList(),
                    baseContinuationClasses = transformedAnnotation.baseContinuationClasses.map { it.java },
                    version = transformedAnnotation.version
                )
            }
        } catch (_: GenericSignatureFormatError) {
            if (annotationMetadataResolver != null) {
                try {
                    clazz.classLoader?.let { loader ->
                        clazz.getBodyStream(loader)?.use {
                            annotationMetadataResolver.getTransformationMetadata(it, loader)
                        }
                    }
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
        }
        if (meta != null && meta.version <= TRANSFORMED_VERSION) {
            val transformedClassSpec = run {
                val lineNumbersByMethod = meta.methods.asSequence()
                    .map { it.name to it.lineNumbers }
                    .toMap()
                val baseContinuationClasses = meta.baseContinuationClasses.asSequence()
                    .filter { BaseContinuation::class.java.isAssignableFrom(it) }
                    .map {
                        @Suppress("UNCHECKED_CAST")
                        it as Class<out BaseContinuation>
                    }
                    .toSet()
                TransformedClassSpec(
                    transformedClass = clazz,
                    fileName = meta.fileName,
                    lookup = lookup,
                    lineNumbersByMethod = lineNumbersByMethod,
                    baseContinuationClasses = baseContinuationClasses
                )
            }
            _transformedClasses[clazz.name] = transformedClassSpec
            callListeners(transformedClassSpec)
        } else if (meta != null) {
            error("Class [$clazz] has transformed meta of version [${meta.version}]. Please update Decoroutinator")
        }
    }

    private val _transformedClasses: MutableMap<String, TransformedClassSpec> = ConcurrentHashMap()
    private val listeners: MutableList<Listener> = CopyOnWriteArrayList()

    private fun callListeners(spec: TransformedClassSpec) {
        listeners.forEach {
            try {
                it.onNewTransformedClass(spec)
            } catch (exception: Throwable) {
                try {
                    it.onException(exception)
                } catch (_: Throwable) {}
            }
        }
    }
}
