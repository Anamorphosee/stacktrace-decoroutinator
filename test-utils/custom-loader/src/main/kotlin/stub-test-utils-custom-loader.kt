@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.test

import dev.reformator.bytecodeprocessor.intrinsics.currentFileName
import dev.reformator.bytecodeprocessor.intrinsics.currentLineNumber
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

@Suppress("unused")
class ClassWithSuspendFunctionsStub {
    fun performCheck(allowTailCallOptimization: Boolean) {
        runBlocking {
            if (allowTailCallOptimization) {
                tailCallOptimizedCheck()
            } else {
                basicCheck()
            }
        }
    }

    suspend fun tailCallOptimizedCheck() {
        val lineNumber = currentLineNumber + 1
        check(StackTraceElement(
            ClassWithSuspendFunctionsStub::class.java.name,
            ::tailCallOptimizedCheck.name,
            currentFileName,
            lineNumber
        ))
    }

    suspend fun basicCheck() {
        val lineNumber = currentLineNumber + 1
        check(StackTraceElement(
            ClassWithSuspendFunctionsStub::class.java.name,
            ::basicCheck.name,
            currentFileName,
            lineNumber
        ))
        tailCallDeoptimizer()
    }

    suspend fun check(parentFrame: StackTraceElement) {
        yield()
        checkStacktrace(parentFrame)
    }

    private fun tailCallDeoptimizer() { }
}