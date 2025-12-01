@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.test

import dev.reformator.retracerepack.obfuscate.MappingReader
import dev.reformator.retracerepack.retrace.FrameInfo
import dev.reformator.retracerepack.retrace.FrameRemapper
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File

private var mappers = emptyList<FrameRemapper>()

fun setRetraceMappingFiles(vararg files: String) {
    mappers = files.map { file ->
        val reader = MappingReader(File(file))
        FrameRemapper().apply { reader.pump(this) }
    }
}

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

private infix fun StackTraceElement.eq(other: StackTraceElement): Boolean {
    if (
        className == other.className &&
        methodName == other.methodName &&
        fileName == other.fileName &&
        lineNumber == other.lineNumber
    ) return true

    fun StackTraceElement.toFrameInfo() =
        FrameInfo(className, null, lineNumber, null, null, methodName, null)

    val frame1 = toFrameInfo()
    val frame2 = other.toFrameInfo()
    mappers.forEach { mapper ->
        infix fun FrameInfo.deobfuscateEq(other: FrameInfo) =
            mapper.transform(this).orEmpty().any { deobfuscatedFrame ->
                deobfuscatedFrame.className == other.className && deobfuscatedFrame.methodName == other.methodName &&
                    deobfuscatedFrame.lineNumber == other.lineNumber
            }

        if (frame1 deobfuscateEq frame2 || frame2 deobfuscateEq frame1) {
            return true
        }
    }

    return false
}

typealias Junit4Test = org.junit.Test
typealias Junit5Test = org.junit.jupiter.api.Test
