@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.generator.internal

import dev.reformator.stacktracedecoroutinator.common.internal.TransformationMetadata
import dev.reformator.stacktracedecoroutinator.common.internal.AnnotationMetadataResolver
import dev.reformator.stacktracedecoroutinator.common.internal.KotlinDebugMetadata
import dev.reformator.stacktracedecoroutinator.common.internal.parseTransformationMetadata
import dev.reformator.stacktracedecoroutinator.intrinsics.DebugMetadata
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorTransformed
import java.io.InputStream

class AnnotationMetadataResolverImpl: AnnotationMetadataResolver {
    @Suppress("UNCHECKED_CAST")
    override fun getTransformationMetadata(classBody: InputStream, loader: ClassLoader): TransformationMetadata? {
        val clazz = getClassNode(classBody, skipCode = true) ?: return null
        val transformedAnnotation = clazz.decoroutinatorTransformedAnnotation ?: return null
        val fileNamePresent = transformedAnnotation.getField(DecoroutinatorTransformed::fileNamePresent.name) as Boolean?
        val fileName = transformedAnnotation.getField(DecoroutinatorTransformed::fileName.name) as String?
        val methodNames = transformedAnnotation.getField(DecoroutinatorTransformed::methodNames.name) as List<String>
        val lineNumbersCounts = transformedAnnotation.getField(DecoroutinatorTransformed::lineNumbersCounts.name) as List<Int>
        val lineNumbers = transformedAnnotation.getField(DecoroutinatorTransformed::lineNumbers.name) as List<Int>
        val baseContinuationClasses = transformedAnnotation.getField(DecoroutinatorTransformed::baseContinuationClasses.name) as List<String>
        val version = transformedAnnotation.getField(DecoroutinatorTransformed::version.name) as Int
        return parseTransformationMetadata(
            fileNamePresent = fileNamePresent,
            fileName = fileName,
            methodNames = methodNames,
            lineNumbersCounts = lineNumbersCounts,
            lineNumbers = lineNumbers,
            baseContinuationClasses = baseContinuationClasses.toSet(),
            version = version
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