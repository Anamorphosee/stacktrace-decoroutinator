@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.test

import org.junit.jupiter.api.Assertions.assertTrue

fun Array<StackTraceElement>.checkStacktrace(vararg elements: StackTraceElement, fromIndex: Int = 0) {
    if (elements.isEmpty()) {
        return
    }
    var startIndex = fromIndex
    while (!(this[startIndex] eq elements[0])) startIndex++
    elements.forEachIndexed { index, element ->
        assertTrue(element eq this[startIndex + index])
    }
}

fun checkStacktrace(vararg elements: StackTraceElement) {
    Exception().stackTrace.checkStacktrace(*elements)
}

private infix fun StackTraceElement.eq(element: StackTraceElement) =
    this.className == element.className && this.methodName == element.methodName && this.lineNumber == element.lineNumber

typealias Junit4Test = org.junit.Test
typealias Junit5Test = org.junit.jupiter.api.Test
