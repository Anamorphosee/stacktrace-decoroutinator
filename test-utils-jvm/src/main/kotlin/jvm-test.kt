@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.testjvm

import dev.reformator.bytecodeprocessor.intrinsics.currentFileName
import dev.reformator.bytecodeprocessor.intrinsics.currentLineNumber
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test


open class JvmTest {
    @Test
    fun `load method with spaces`() = runBlocking {
        yield()
    }

    fun `class with spaces`(allowTailCallsOptimization: Boolean) = runBlocking {
        if (allowTailCallsOptimization) {
            checkTailCallsOptimized()
        } else {
            checkWithoutTailCallsOptimization()
        }
    }

    suspend fun checkTailCallsOptimized() {
        val lineNumber = currentLineNumber + 1
        check(StackTraceElement(
            JvmTest::class.java.name,
            JvmTest::checkTailCallsOptimized.name,
            currentFileName,
            lineNumber
        ))
        tailCallDeoptimize()
    }

    suspend fun checkWithoutTailCallsOptimization() {
        val lineNumber = currentLineNumber + 1
        check(StackTraceElement(
            JvmTest::class.java.name,
            JvmTest::checkWithoutTailCallsOptimization.name,
            currentFileName,
            lineNumber
        ))
    }

    suspend fun check(parent: StackTraceElement) {
        yield()
        checkStacktrace(parent)
    }
}

private fun tailCallDeoptimize() { }
