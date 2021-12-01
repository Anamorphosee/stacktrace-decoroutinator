package dev.reformator.stacktracedecoroutinator.registry

import kotlin.coroutines.Continuation
import kotlin.jvm.Throws

data class ContinuationStacktraceElementSummary(
    val className: String,
    val fileName: String?,
    val methodName: String,
    val possibleLineNumbers: Set<Int>
)

data class ContinuationStacktraceElement(
    private val summary: ContinuationStacktraceElementSummary,
    val lineNumber: Int
) {
    val className: String
    get() = summary.className

    val fileName: String?
    get() = summary.fileName

    val methodName: String
    get() = summary.methodName

    val possibleLineNumbers: Set<Int>
    get() = summary.possibleLineNumbers
}

interface DecoroutinatorContinuationStacktraceElementRegistry {
    @Throws(Throwable::class)
    fun getStacktraceElement(continuation: Continuation<*>): ContinuationStacktraceElement?
}
