@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.generatorjvm.internal

import dev.reformator.stacktracedecoroutinator.common.internal.TransformationMetadata
import dev.reformator.stacktracedecoroutinator.common.internal.AnnotationMetadataResolver
import dev.reformator.stacktracedecoroutinator.common.internal.KotlinDebugMetadata
import dev.reformator.stacktracedecoroutinator.common.internal.parseTransformationMetadata
import dev.reformator.stacktracedecoroutinator.intrinsics.DebugMetadata
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorTransformed
import dev.reformator.stacktracedecoroutinator.specmethodbuilder.internal.decoroutinatorTransformedAnnotation
import dev.reformator.stacktracedecoroutinator.specmethodbuilder.internal.getClassNode
import dev.reformator.stacktracedecoroutinator.specmethodbuilder.internal.getField
import dev.reformator.stacktracedecoroutinator.specmethodbuilder.internal.kotlinDebugMetadataAnnotation
import java.io.InputStream

class AnnotationMetadataResolverImpl: AnnotationMetadataResolver {
    @Suppress("UNCHECKED_CAST")
    override fun getTransformationMetadata(classBody: InputStream): TransformationMetadata? {
        val clazz = getClassNode(classBody, skipCode = true) ?: return null
        val transformedAnnotation = clazz.decoroutinatorTransformedAnnotation ?: return null
        val fileNamePresent = transformedAnnotation.getField(DecoroutinatorTransformed::fileNamePresent.name) as Boolean?
        val fileName = transformedAnnotation.getField(DecoroutinatorTransformed::fileName.name) as String?
        val methodNames = transformedAnnotation.getField(DecoroutinatorTransformed::methodNames.name) as List<String>
        val lineNumbersCounts = transformedAnnotation.getField(DecoroutinatorTransformed::lineNumbersCounts.name) as List<Int>
        val lineNumbers = transformedAnnotation.getField(DecoroutinatorTransformed::lineNumbers.name) as List<Int>
        val skipSpecMethods = transformedAnnotation.getField(DecoroutinatorTransformed::skipSpecMethods.name) as Boolean?
        return parseTransformationMetadata(
            fileNamePresent = fileNamePresent,
            fileName = fileName,
            methodNames = methodNames,
            lineNumbersCounts = lineNumbersCounts,
            lineNumbers = lineNumbers,
            skipSpecMethods = skipSpecMethods
        )
    }

    @Suppress("UNCHECKED_CAST")
    override fun getKotlinDebugMetadata(classBody: InputStream): KotlinDebugMetadata? {
        val clazz = getClassNode(classBody, skipCode = true) ?: return null
        val metadataAnnotation = clazz.kotlinDebugMetadataAnnotation ?: return null
        return KotlinDebugMetadata(
            sourceFile = metadataAnnotation.getField(DebugMetadata::f.name) as String,
            className = metadataAnnotation.getField(DebugMetadata::c.name) as String,
            methodName = metadataAnnotation.getField(DebugMetadata::m.name) as String,
            lineNumbers = (metadataAnnotation.getField(DebugMetadata::l.name) as List<Int>).toIntArray(),
        )
    }
}