@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.test

import kotlin.test.assertTrue

fun getLineNumber(): Int {
    val stacktrace = Exception().stackTrace
    val stacktraceIndex = stacktrace.indexOfFirst {
        it.methodName == "getLineNumber"
    } + 1
    return stacktrace[stacktraceIndex].lineNumber
}

fun getFileName(): String {
    val stacktrace = Exception().stackTrace
    val stacktraceIndex = stacktrace.indexOfFirst {
        it.methodName == "getFileName"
    } + 1
    return stacktrace[stacktraceIndex].fileName!!
}

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
