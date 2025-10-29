@file:Suppress("NewApi", "PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorTransformed
import java.lang.invoke.MethodHandles
import java.lang.reflect.GenericSignatureFormatError
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

internal class TransformedClassesRegistryImpl: TransformedClassesRegistry {
    private val _transformedClasses: MutableMap<Class<*>, TransformedClassesRegistry.TransformedClassSpec> =
        ConcurrentHashMap()
    private val listeners: MutableList<TransformedClassesRegistry.Listener> = CopyOnWriteArrayList()

    override val transformedClasses: Collection<TransformedClassesRegistry.TransformedClassSpec>
        get() {
            while (true) {
                try {
                    return ArrayList(_transformedClasses.values)
                } catch (_: ConcurrentModificationException) { }
            }
        }

    override fun get(clazz: Class<*>): TransformedClassesRegistry.TransformedClassSpec? =
        _transformedClasses[clazz]

    override fun addListener(listener: TransformedClassesRegistry.Listener) {
        listeners.add(listener)
    }

    override fun registerTransformedClass(lookup: MethodHandles.Lookup) {
        val clazz: Class<*> = lookup.lookupClass()
        val loader = clazz.classLoader ?: ClassLoader.getSystemClassLoader()
        val meta = try {
            clazz.getDeclaredAnnotation(DecoroutinatorTransformed::class.java)?.let { transformedAnnotation ->
                parseTransformationMetadata(
                    fileNamePresent = transformedAnnotation.fileNamePresent,
                    fileName = transformedAnnotation.fileName,
                    methodNames = transformedAnnotation.methodNames.toList(),
                    lineNumbersCounts = transformedAnnotation.lineNumbersCounts.toList(),
                    lineNumbers = transformedAnnotation.lineNumbers.toList(),
                    skipSpecMethods = transformedAnnotation.skipSpecMethods
                )
            }
        // https://youtrack.jetbrains.com/issue/KT-25337
        } catch (_: GenericSignatureFormatError) {
            if (annotationMetadataResolver != null) {
                try {
                    clazz.getBodyStream(loader)?.use {
                        annotationMetadataResolver.getTransformationMetadata(it)
                    }
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
        }
        if (meta != null) {
            val transformedClassSpec = run {
                val lineNumbersByMethod = meta.methods.asSequence()
                    .map { it.name to it.lineNumbers }
                    .toMap(
                        if (meta.methods.size < methodsNumberThreshold) {
                            CompactMap()
                        } else {
                            newHashMapForSize(meta.methods.size)
                        }
                    )
                TransformedClassesRegistry.TransformedClassSpec(
                    transformedClass = clazz,
                    fileName = meta.fileName,
                    lookup = lookup,
                    lineNumbersByMethod = lineNumbersByMethod,
                    skipSpecMethods = meta.skipSpecMethods
                )
            }
            _transformedClasses[clazz] = transformedClassSpec
            callListeners(transformedClassSpec)
        }
    }

    private fun callListeners(spec: TransformedClassesRegistry.TransformedClassSpec) {
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
