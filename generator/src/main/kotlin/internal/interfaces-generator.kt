@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.generator.internal

class DebugMetadataInfo internal constructor(
    internal val specClassInternalClassName: String,
    internal val baseContinuationInternalClassName: String,
    internal val methodName: String,
    internal val fileName: String?,
    internal val lineNumbers: Set<Int>
)
