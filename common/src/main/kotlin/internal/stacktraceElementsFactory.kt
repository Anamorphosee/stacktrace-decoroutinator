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
    override fun getStacktraceElements(continuations: Collection<BaseContinuation>): StacktraceElements {
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
                val spec = getBaseContinuationClassSpec(baseContinuationClass.name)
                val elementsByLabel = spec.getElementsByLabel(baseContinuationClass)
                if (elementsByLabel != null) {
                    continuations.forEach { continuation ->
                        val label = spec.labelExtractor.getLabel(continuation)
                        val element = elementsByLabel[if (label == UNKNOWN_LABEL) 0 else label]
                        elementsByContinuation[continuation] = element
                    }
                    possibleElements.addAll(elementsByLabel)
                }
            }
        }
        return StacktraceElements(
            elementsByContinuation = elementsByContinuation,
            possibleElements = possibleElements
        )
    }

    override fun getLabelExtractor(continuation: BaseContinuation): StacktraceElementsFactory.LabelExtractor {
        assert { continuation.javaClass !== DecoroutinatorContinuationImpl::class.java }
        val spec = getBaseContinuationClassSpec(continuation.javaClass.name)
        return spec.labelExtractor
    }

    private val specsByClassName: MutableMap<String, BaseContinuationClassSpec> = ConcurrentHashMap()
    @Volatile private var specsByClassNameSnapshot: Map<String, BaseContinuationClassSpec> = emptyMap()

    private fun getBaseContinuationClassSpec(
        baseContinuationClassName: String,
        new: () -> BaseContinuationClassSpec = { BaseContinuationClassSpec(ReflectionLabelExtractor()) },
        updateSnapshot: Boolean = true
    ): BaseContinuationClassSpec {
        var updated = false
        val result = specsByClassNameSnapshot[baseContinuationClassName] ?: run {
            updated = true
            specsByClassName.computeIfAbsent(baseContinuationClassName) { _ -> new() }
        }
        if (updateSnapshot && updated) {
            while (true) {
                try {
                    specsByClassNameSnapshot = HashMap(specsByClassName)
                    break
                } catch (_: ConcurrentModificationException) { }
            }
        }
        return result
    }

    init {
        if (methodHandleInvoker.supportsVarHandle) {
            TransformedClassesRegistry.addListener(::updateLabelExtractor)
            TransformedClassesRegistry.transformedClasses.forEach(::updateLabelExtractor)
        }
    }


    private class BaseContinuationClassSpec(
        @Volatile var labelExtractor: StacktraceElementsFactory.LabelExtractor
    ) {
        fun getElementsByLabel(clazz: Class<out BaseContinuation>): List<StacktraceElement>? {
            val localElementsByLabel = elementsByLabel ?: run {
                val meta = try {
                    clazz.getAnnotation(DebugMetadata::class.java)?.let { debugMetadataAnnotation ->
                        KotlinDebugMetadata(
                            sourceFile = debugMetadataAnnotation.f,
                            className = debugMetadataAnnotation.c,
                            methodName = debugMetadataAnnotation.m,
                            lineNumbers = debugMetadataAnnotation.l
                        )
                    }
                // https://youtrack.jetbrains.com/issue/KT-25337
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
                val newElementsByLabel = if (meta != null) {
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
                    failedElementsByLabel
                }
                elementsByLabel = newElementsByLabel
                newElementsByLabel
            }
            return if (localElementsByLabel != failedElementsByLabel) localElementsByLabel else null
        }

        var elementsByLabel: List<StacktraceElement>? = null

        private companion object {
            val failedElementsByLabel = emptyList<StacktraceElement>()
        }
    }

    private open class ReflectionLabelExtractor: StacktraceElementsFactory.LabelExtractor {
        override fun getLabel(continuation: BaseContinuation): Int {
            val field = getField(continuation)
            return if (field != null) {
                try {
                    field[continuation] as Int
                } catch (_: Throwable) {
                    UNKNOWN_LABEL
                }
            } else {
                UNKNOWN_LABEL
            }
        }

        private var field: Field? = null

        private fun getField(baseContinuationClass: BaseContinuation): Field? {
            val localField: Field = this.field ?: run {
                var localField = try {
                    baseContinuationClass.javaClass.getDeclaredField(LABEL_FIELD_NAME)
                } catch (_: Throwable) { failedField }
                try {
                    localField.isAccessible = true
                } catch (_: Throwable) { localField = failedField }
                this.field = localField
                localField
            }
            return if (localField != failedField) localField else null
        }

        private companion object {
            @JvmField protected val _failedField = 0
            val failedField: Field = ReflectionLabelExtractor::class.java.getDeclaredField(::_failedField.name)
        }
    }

    private class VarHandleLabelExtractor(
        private val lookup: MethodHandles.Lookup,
    ): ReflectionLabelExtractor() {
        override fun getLabel(continuation: BaseContinuation): Int =
            getField(continuation)?.let { field ->
                try {
                    varHandleInvoker!!.getIntVar(field, continuation)
                } catch (_: Throwable) { null }
            } ?: super.getLabel(continuation)

        private var field: VarHandle? = null

        private fun getField(baseContinuation: BaseContinuation): VarHandle? {
            val localField: VarHandle = this.field ?: run {
                val newField = try {
                    lookup.findVarHandle(baseContinuation.javaClass, LABEL_FIELD_NAME, Int::class.javaPrimitiveType)
                } catch (_: Throwable) {
                    failedField
                }
                field = newField
                newField
            }
            return if (localField != failedField) localField else null
        }

        private companion object {
            @JvmField protected val _failedField = 0
            val failedField: VarHandle =
                MethodHandles.lookup().findStaticVarHandle(
                    VarHandleLabelExtractor::class.java,
                    ::_failedField.name,
                    Int::class.javaPrimitiveType
                )
        }
    }

    private fun updateLabelExtractor(spec: TransformedClassesRegistry.TransformedClassSpec) {
        spec.baseContinuationClasses.forEach { baseContinuationClassName ->
            val newLabelExtractor = VarHandleLabelExtractor(spec.lookup)
            getBaseContinuationClassSpec(
                baseContinuationClassName = baseContinuationClassName,
                new = { BaseContinuationClassSpec(newLabelExtractor) },
                updateSnapshot = false
            ).labelExtractor = newLabelExtractor
        }
    }
}

private const val LABEL_FIELD_NAME = "label"
