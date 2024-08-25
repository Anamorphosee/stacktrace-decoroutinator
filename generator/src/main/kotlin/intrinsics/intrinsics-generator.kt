@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.generator.intrinsics

import dev.reformator.bytecodeprocessor.intrinsics.ChangeClassName
import dev.reformator.stacktracedecoroutinator.common.internal.DEBUG_METADATA_CLASS_NAME

@ChangeClassName(
    toName = DEBUG_METADATA_CLASS_NAME,
    deleteAfterChanging = true
)
@Target(AnnotationTarget.CLASS)
internal annotation class DebugMetadata(
    @get:JvmName("f")
    val sourceFile: String = "",
    @get:JvmName("l")
    val lineNumbers: IntArray = [],
    @get:JvmName("m")
    val methodName: String = "",
    @get:JvmName("c")
    val className: String = ""
)
