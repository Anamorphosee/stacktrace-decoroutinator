package dev.reformator.stacktracedecoroutinator.utils

import dev.reformator.stacktracedecoroutinator.common.getFileClass
import kotlin.test.assertTrue

private val className = getFileClass {  }.name

fun checkStacktrace(vararg elements: StackTraceElement) {
    if (elements.isEmpty()) {
        return
    }
    Exception().stackTrace.also { stacktrace ->
        val startIndex = stacktrace.indexOfFirst { it eq elements[0] }
        elements.forEachIndexed { index, element ->
            assertTrue(element eq stacktrace[startIndex + index])
        }
    }
}

private infix fun StackTraceElement.eq(element: StackTraceElement) =
    this.className == element.className && this.methodName == element.methodName &&
            this.lineNumber == element.lineNumber

fun getLineNumber(): Int {
    val stacktrace = Exception().stackTrace
    val stacktraceIndex = stacktrace.indexOfFirst {
        it.className == className
    } + 1
    return stacktrace[stacktraceIndex].lineNumber
}

