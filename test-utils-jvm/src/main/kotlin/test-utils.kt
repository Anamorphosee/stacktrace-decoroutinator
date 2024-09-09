@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.testjvm

import org.junit.jupiter.api.Assertions.assertTrue

fun checkStacktrace(vararg elements: StackTraceElement) {
    if (elements.isEmpty()) {
        return
    }
    Exception().stackTrace.also { stacktrace ->
        val startIndex = stacktrace.indexOfFirst { elements[0] eq it }
        elements.forEachIndexed { index, element ->
            assertTrue(element eq stacktrace[startIndex + index])
        }
    }
}

private infix fun StackTraceElement.eq(element: StackTraceElement) =
    this.className == element.className && (this.methodName == element.methodName || this.methodName == ANY) &&
            this.lineNumber == element.lineNumber

const val ANY = "<any>"
