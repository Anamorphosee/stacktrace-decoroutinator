package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.DebugMetadata
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import java.lang.reflect.Field
import java.lang.reflect.GenericSignatureFormatError
import java.util.concurrent.ConcurrentHashMap

internal object StacktraceElementsFactoryImpl: StacktraceElementsFactory {
    @Suppress("UNCHECKED_CAST")
    override fun getStacktraceElements(continuations: Set<BaseContinuation>): StacktraceElements {
        val elementsByContinuation = mutableMapOf<BaseContinuation, StacktraceElement>()
        val possibleElements = mutableSetOf<StacktraceElement>()
        continuations.groupBy { it.javaClass }.forEach { (baseContinuationClass, continuations) ->
            if (baseContinuationClass == DecoroutinatorContinuationImpl::class.java) {
                (continuations as List<DecoroutinatorContinuationImpl>).forEach { continuation ->
                    val element = StacktraceElement(
                        fileName = continuation.fileName,
                        className = continuation.className,
                        methodName = continuation.methodName,
                        lineNumber = continuation.lineNumber
                    )
                    elementsByContinuation[continuation] = element
                    possibleElements.add(element)
                }
            } else {
                val spec = specs.computeIfAbsent(baseContinuationClass) { _ ->
                    BaseContinuationClassSpec(
                        clazz = baseContinuationClass,
                        labelExtractor = DefaultLabelExtractor(baseContinuationClass)
                    )
                }
                if (spec.elementsByLabel != null) {
                    continuations.forEach { continuation ->
                        val label = @Suppress("UNCHECKED_CAST")
                        (spec.labelExtractor as LabelExtractor<BaseContinuation>).getLabel(continuation)
                        val element = spec.elementsByLabel[if (label == UNKNOWN_LABEL) 0 else label]
                        elementsByContinuation[continuation] = element
                    }
                    possibleElements.addAll(spec.elementsByLabel)
                }
            }
        }
        return StacktraceElements(
            elementsByContinuation = elementsByContinuation,
            possibleElements = possibleElements
        )
    }

    private val specs: MutableMap<Class<out BaseContinuation>, BaseContinuationClassSpec<*>> = ConcurrentHashMap()

    init {
        if (supportsVarHandles) {
            TransformedClassesRegistry.addListener(::updateLabelExtractor)
            TransformedClassesRegistry.transformedClasses.forEach(::updateLabelExtractor)
        }
    }

    private fun interface LabelExtractor<in T: BaseContinuation> {
        fun getLabel(baseContinuation: T): Int
    }

    private class BaseContinuationClassSpec<T: BaseContinuation>(
        clazz: Class<T>,
        var labelExtractor: LabelExtractor<T>,
    ) {
        val elementsByLabel: List<StacktraceElement>? = run {
            val meta = try {
                clazz.getAnnotation(DebugMetadata::class.java)?.let { debugMetadataAnnotation ->
                    KotlinDebugMetadata(
                        sourceFile = debugMetadataAnnotation.f,
                        className = debugMetadataAnnotation.c,
                        methodName = debugMetadataAnnotation.m,
                        lineNumbers = debugMetadataAnnotation.l
                    )
                }
            } catch (_: GenericSignatureFormatError) {
                if (annotationMetadataResolver != null) {
                    try {
                        clazz.getBodyStream()?.use { annotationMetadataResolver.getKotlinDebugMetadata(it) }
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    null
                }
            }
            if (meta != null) {
                List(meta.lineNumbers.size + 1) { index ->
                    val lineNumber = if (index == 0) UNKNOWN_LINE_NUMBER else meta.lineNumbers[index - 1]
                    StacktraceElement(
                        className = meta.className,
                        fileName = meta.sourceFile.ifEmpty { null },
                        methodName = meta.methodName,
                        lineNumber = lineNumber
                    )
                }
            } else {
                null
            }
        }
    }

    private open class DefaultLabelExtractor<in T: BaseContinuation>(clazz: Class<T>): LabelExtractor<T> {
        private val labelField: Field? = run {
            val field = try {
                clazz.getDeclaredField(LABEL_FIELD_NAME)
            } catch (_: Throwable) { null }
            try {
                field?.isAccessible = true
            } catch (_: Throwable) {}
            field
        }

        override fun getLabel(baseContinuation: T): Int =
            if (labelField != null) {
                try {
                    labelField[baseContinuation] as Int
                } catch (_: Throwable) { UNKNOWN_LABEL }
            } else { UNKNOWN_LABEL }
    }

    private class VarHandleLabelExtractor<in T: BaseContinuation>(
        lookup: MethodHandles.Lookup,
        clazz: Class<T>
    ): DefaultLabelExtractor<T>(clazz) {
        private val labelHandle: VarHandle? = try {
            lookup.findVarHandle(clazz, LABEL_FIELD_NAME, Int::class.javaPrimitiveType)
        } catch (_: Throwable) { null }

        override fun getLabel(baseContinuation: T): Int =
            if (labelHandle != null) {
                try {
                    labelHandle[baseContinuation] as Int
                } catch (_: Throwable) {
                    super.getLabel(baseContinuation)
                }
            } else {
                super.getLabel(baseContinuation)
            }
    }

    private fun <T: BaseContinuation> updateLabelExtractor(clazz: Class<T>, lookup: MethodHandles.Lookup) {
        val newLabelExtractor = VarHandleLabelExtractor(
            lookup = lookup,
            clazz = clazz
        )
        specs.compute(clazz) { _, oldSpec: BaseContinuationClassSpec<*>? ->
            @Suppress("UNCHECKED_CAST")
            oldSpec as BaseContinuationClassSpec<T>?
            if (oldSpec != null) {
                oldSpec.labelExtractor = newLabelExtractor
                oldSpec
            } else {
                BaseContinuationClassSpec(clazz, newLabelExtractor)
            }
        }
    }

    private fun updateLabelExtractor(spec: TransformedClassesRegistry.TransformedClassSpec) {
        spec.baseContinuationClasses.forEach { updateLabelExtractor(it, spec.lookup) }
    }
}

private const val UNKNOWN_LABEL = -1
private const val LABEL_FIELD_NAME = "label"
