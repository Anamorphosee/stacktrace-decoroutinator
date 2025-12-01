@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.testjvm

import dev.reformator.bytecodeprocessor.intrinsics.currentFileName
import dev.reformator.bytecodeprocessor.intrinsics.currentLineNumber
import dev.reformator.bytecodeprocessor.intrinsics.ownerClassName
import dev.reformator.bytecodeprocessor.intrinsics.ownerMethodName
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test


open class JvmTest {
    @Test
    fun `load method with spaces`() = runBlocking {
        yield()
    }

    @Test
    fun `method with spaces`() = runBlocking {
        val lineNumber = currentLineNumber + 1
        check(StackTraceElement(
            ownerClassName,
            ownerMethodName,
            currentFileName,
            lineNumber
        ))
        tailCallDeoptimize()
    }
}

open class JvmTailCallDeoptimizedTest {
    @Test
    fun `method with spaces`() = runBlocking {
        val lineNumber = currentLineNumber + 1
        check(StackTraceElement(
            ownerClassName,
            ownerMethodName,
            currentFileName,
            lineNumber
        ))
    }
}

private fun tailCallDeoptimize() { }

private suspend fun check(parent: StackTraceElement) {
    yield()
    checkStacktrace(parent)
}
