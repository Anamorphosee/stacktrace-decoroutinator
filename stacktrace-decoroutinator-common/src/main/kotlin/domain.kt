package dev.reformator.stacktracedecoroutinator

data class DecoroutinatorStacktraceElement(
    val className: String,
    val fileName: String?,
    val methodName: String,
    val lineNumber: Int
)
