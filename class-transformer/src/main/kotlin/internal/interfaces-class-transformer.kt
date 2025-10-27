package dev.reformator.stacktracedecoroutinator.classtransformer.internal

class DebugMetadataInfo internal constructor(
    internal val specClassInternalClassName: String,
    internal val baseContinuationInternalClassName: String,
    internal val methodName: String,
    internal val lineNumbers: Set<Int>
)
