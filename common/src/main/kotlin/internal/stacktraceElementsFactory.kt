@file:Suppress("PackageDirectoryMismatch", "NewApi")

package dev.reformator.stacktracedecoroutinator.common.internal

import dev.reformator.stacktracedecoroutinator.intrinsics.DebugMetadata
import dev.reformator.stacktracedecoroutinator.intrinsics.BaseContinuation
import dev.reformator.stacktracedecoroutinator.provider.internal.AndroidKeep
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import java.lang.reflect.Field
import java.lang.reflect.GenericSignatureFormatError
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val LABEL_FIELD_NAME = "label"

internal class StacktraceElementsFactoryImpl: StacktraceElementsFactory {
    private val specsByBaseContinuationClassName: MutableMap<String, BaseContinuationClassSpec> = HashMap()
    private val updateLock = ReentrantLock()

    init {
        if (methodHandleInvoker.supportsVarHandle) {
            TransformedClassesRegistry.addListener(::registerTransformedClassSpec)
            updateLock.withLock {
                TransformedClassesRegistry.transformedClasses.forEach(::registerTransformedClassSpec)
            }
        }
    }

    private fun getBaseContinuationClassSpec(baseContinuationClassName: String): BaseContinuationClassSpec =
        specsByBaseContinuationClassName.optimisticLockGetOrPut(
            key = baseContinuationClassName,
            lock = updateLock
        ) { BaseContinuationClassSpec() }

    private fun registerTransformedClassSpec(spec: TransformedClassesRegistry.TransformedClassSpec) {
        updateLock.withLock {
            spec.baseContinuationClasses.forEach { baseContinuationClassName ->
                getBaseContinuationClassSpec(baseContinuationClassName).labelExtractor =
                    VarHandleLabelExtractor(spec.lookup)
            }
        }
    }

    override fun getStacktraceElement(baseContinuation: BaseContinuation): StackTraceElement? {
        val baseContinuationClass = baseContinuation.javaClass
        val spec = getBaseContinuationClassSpec(baseContinuationClass.name)
        val elementsByLabel = spec.getElementsByLabel(baseContinuationClass) ?: return null
        val label = spec.labelExtractor.getLabel(baseContinuation)
        return elementsByLabel[if (label == UNKNOWN_LABEL) 0 else label]
    }

    override fun getLabelExtractor(continuation: BaseContinuation): StacktraceElementsFactory.LabelExtractor {
        assert { continuation.javaClass != DecoroutinatorContinuationImpl::class.java }
        val spec = getBaseContinuationClassSpec(continuation.javaClass.name)
        return spec.labelExtractor
    }

    companion object {
        val failedElementsByLabel = emptyArray<StackTraceElement>()
    }

    private class BaseContinuationClassSpec {
        var labelExtractor: StacktraceElementsFactory.LabelExtractor = ReflectionLabelExtractor()
        var elementsByLabel: Array<StackTraceElement>? = null
    }

    private fun BaseContinuationClassSpec.getElementsByLabel(
        clazz: Class<out BaseContinuation>
    ): Array<StackTraceElement>? {
        val elementsByLabel = elementsByLabel ?: run {
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
                val className = meta.className
                Array(meta.lineNumbers.size + 1) { index ->
                    val lineNumber = if (index == 0) UNKNOWN_LINE_NUMBER else meta.lineNumbers[index - 1]
                    StackTraceElement(
                        className,
                        meta.methodName,
                        meta.sourceFile.ifEmpty { null },
                        lineNumber
                    )
                }
            } else {
                failedElementsByLabel
            }
            elementsByLabel = newElementsByLabel
            newElementsByLabel
        }
        return if (elementsByLabel !== failedElementsByLabel) elementsByLabel else null
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

        @AndroidKeep
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

        @AndroidKeep
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
