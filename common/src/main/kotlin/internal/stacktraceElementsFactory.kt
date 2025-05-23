@file:Suppress("PackageDirectoryMismatch", "NewApi")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.DebugMetadata
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import java.lang.reflect.Field
import java.lang.reflect.GenericSignatureFormatError
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class StacktraceElementsFactoryImpl: StacktraceElementsFactory {
    private val possibleElementsBySpecClassName: MutableMap<String, Array<StacktraceElement>> = HashMap()
    private val specsByBaseContinuationClassName: MutableMap<String, BaseContinuationClassSpec> = HashMap()
    private val updateLock = ReentrantLock()

    init {
        TransformedClassesRegistry.addListener(::registerTransformedClassSpec)
        updateLock.withLock {
            TransformedClassesRegistry.transformedClasses.forEach(::registerTransformedClassSpec)
        }
    }

    private fun getBaseContinuationClassSpec(baseContinuationClassName: String): BaseContinuationClassSpec =
        try {
            specsByBaseContinuationClassName[baseContinuationClassName]
        } catch (_: ConcurrentModificationException) {
            null
        } ?: updateLock.withLock {
            specsByBaseContinuationClassName[baseContinuationClassName]?.let { return it }
            val newBaseContinuationClassSpec = BaseContinuationClassSpec()
            specsByBaseContinuationClassName[baseContinuationClassName] = newBaseContinuationClassSpec
            newBaseContinuationClassSpec
        }

    private fun getPossibleElements(specClassName: String): Array<StacktraceElement>? =
        try {
            possibleElementsBySpecClassName[specClassName]
        } catch (_: ConcurrentModificationException) {
            updateLock.withLock {
                possibleElementsBySpecClassName[specClassName]
            }
        }

    private fun registerTransformedClassSpec(spec: TransformedClassesRegistry.TransformedClassSpec) {
        updateLock.withLock {
            possibleElementsBySpecClassName[spec.transformedClass.name] = spec.lineNumbersByMethod.asSequence()
                .flatMap { (methodName, lineNumbers) ->
                    lineNumbers.asSequence().map { lineNumber ->
                        StacktraceElement(
                            className = spec.transformedClass.name,
                            fileName = spec.fileName,
                            methodName = methodName,
                            lineNumber = lineNumber
                        )
                    }
                }.toList().toTypedArray()

            if (methodHandleInvoker.supportsVarHandle) {
                spec.baseContinuationClasses.forEach { baseContinuationClassName ->
                    getBaseContinuationClassSpec(baseContinuationClassName).labelExtractor =
                        VarHandleLabelExtractor(spec.lookup)
                }
            }
        }
    }

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
                    getPossibleElements(continuation.className)?.let {
                        possibleElements.addAll(it)
                    }
                    possibleElements.add(element)
                }
            } else {
                val spec = getBaseContinuationClassSpec(baseContinuationClass.name)
                val info = spec.getInfo(baseContinuationClass)
                if (info != null) {
                    continuations.forEach { continuation ->
                        val label = spec.labelExtractor.getLabel(continuation)
                        val element = info.elementsByLabel[if (label == UNKNOWN_LABEL) 0 else label]
                        elementsByContinuation[continuation] = element
                    }
                    possibleElements.addAll(info.possibleElements)
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

    private class BaseContinuationClassSpecInfo(
        val elementsByLabel: Array<StacktraceElement>,
        val possibleElements: Array<StacktraceElement>
    ) {
        companion object {
            val failed = BaseContinuationClassSpecInfo(emptyArray(), emptyArray())
        }
    }


    private inner class BaseContinuationClassSpec {
        var labelExtractor: StacktraceElementsFactory.LabelExtractor = ReflectionLabelExtractor()
        var info: BaseContinuationClassSpecInfo? = null

        fun getInfo(clazz: Class<out BaseContinuation>): BaseContinuationClassSpecInfo? {
            val localInfo = info ?: run {
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
                val newInfo = if (meta != null) {
                    val className = meta.className
                    val elementsByLabel = Array(meta.lineNumbers.size + 1) { index ->
                        val lineNumber = if (index == 0) UNKNOWN_LINE_NUMBER else meta.lineNumbers[index - 1]
                        StacktraceElement(
                            className = className,
                            fileName = meta.sourceFile.ifEmpty { null },
                            methodName = meta.methodName,
                            lineNumber = lineNumber
                        )
                    }
                    val otherPossibleElements = getPossibleElements(className)
                    BaseContinuationClassSpecInfo(
                        elementsByLabel = elementsByLabel,
                        possibleElements = when {
                            otherPossibleElements == null -> elementsByLabel
                            else -> elementsByLabel + otherPossibleElements
                        }
                    )
                } else {
                    BaseContinuationClassSpecInfo.failed
                }
                info = newInfo
                newInfo
            }
            return if (localInfo !== BaseContinuationClassSpecInfo.failed) localInfo else null
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
            return if (localField !== failedField) localField else null
        }

        private companion object {
            @Suppress("ConstPropertyName")
            protected const val _failedField = 0
            val failedField: Field = ReflectionLabelExtractor::class.java.getDeclaredField(::_failedField.name)
        }
    }

    private class VarHandleLabelExtractor(
        private val lookup: MethodHandles.Lookup,
    ): ReflectionLabelExtractor() {
        override fun getLabel(continuation: BaseContinuation): Int =
            getField(continuation)?.let { field ->
                try {
                    varHandleInvoker.getIntVar(field, continuation)
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
            return if (localField !== failedField) localField else null
        }

        private companion object {
            @Suppress("ConstPropertyName")
            private const val _failedField = 0
            val failedField: VarHandle =
                MethodHandles.lookup().findStaticVarHandle(
                    VarHandleLabelExtractor::class.java,
                    ::_failedField.name,
                    Int::class.javaPrimitiveType
                )
        }
    }
}

private const val LABEL_FIELD_NAME = "label"
