package dev.reformator.stacktracedecoroutinator.utils

import kotlin.test.assertEquals

fun checkStacktrace(vararg elements: StackTraceElement) {
    if (elements.isEmpty()) {
        return
    }
    Exception().stackTrace.toList().also { stacktrace ->
        val startIndex = stacktrace.indexOf(elements[0])
        val checkedStacktrace = stacktrace.subList(startIndex, startIndex + elements.size)
        assertEquals(elements.toList(), checkedStacktrace)
    }
}

fun getLineNumber(): Int {
    val stacktrace = Exception().stackTrace
    val stacktraceIndex = stacktrace.indexOfFirst {
        it.className == "dev.reformator.stacktracedecoroutinator.utils.Utils_testKt"
    } + 1
    return stacktrace[stacktraceIndex].lineNumber
}

