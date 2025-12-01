@file:Suppress("PackageDirectoryMismatch", "JUnitMixedFramework")

package dev.reformator.stacktracedecoroutinator.gradlepluginandroidlegacytests

import android.os.Build
import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.currentFileName
import dev.reformator.bytecodeprocessor.intrinsics.currentLineNumber
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorCommonApi
import dev.reformator.stacktracedecoroutinator.test.checkStacktrace
import dev.reformator.stacktracedecoroutinator.test.setRetraceMappingFiles
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.coroutines.resume

open class TestLocalFile {
    @Before
    fun setup() {
        assumeTrue(Build.VERSION.SDK_INT >= 26)
        setRetraceMappingFiles(minifyDebugMappingFile)
    }

    @Test
    fun localTest(): Unit = runBlocking {
        fun1()
    }

    suspend fun fun1() {
        fun2(currentLineNumber)
    }

    suspend fun fun2(fun1LineNumber: Int) {
        val fun1Frame = StackTraceElement(
            TestLocalFile::class.java.name,
            ::fun1.name,
            currentFileName,
            fun1LineNumber
        )
        yield()
        fun3(fun1Frame, currentLineNumber)
    }

    suspend fun fun3(fun1Frame: StackTraceElement, fun2LineNumber: Int) {
        val fun2Frame = StackTraceElement(
            TestLocalFile::class.java.name,
            ::fun2.name,
            currentFileName,
            fun2LineNumber
        )
        yield()
        checkStacktrace(fun2Frame, fun1Frame)
    }
}

open class TailCallDeoptimizeTest: dev.reformator.stacktracedecoroutinator.test.TailCallDeoptimizeTest() {
    @Before
    fun setup() {
        assumeTrue(Build.VERSION.SDK_INT >= 26)
        setRetraceMappingFiles(minifyDebugMappingFile)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
open class DebugProbesTest {
    @Test
    open fun performDebugProbes() {
        runBlocking {
            suspendCancellableCoroutine { continuation ->
                assertTrue(DebugProbes.dumpCoroutinesInfo().isNotEmpty())
                continuation.resume(Unit)
            }
        }
    }
}

open class RuntimeTest: dev.reformator.stacktracedecoroutinator.test.RuntimeTest() {
    @Before
    fun setup() {
        assumeTrue(Build.VERSION.SDK_INT >= 26)
        setRetraceMappingFiles(minifyDebugMappingFile)
    }
}

open class OldAndroidTest {
    @Before
    fun setup() {
        assumeTrue(Build.VERSION.SDK_INT < 26)
    }

    @Test
    fun checkStatus() {
        val status = DecoroutinatorCommonApi.getStatus { it() }
        assertFalse(status.successful, status.description)
    }
}

@get:LoadConstant("minifyDebugMappingFile")
private val minifyDebugMappingFile: String
    get() = fail()
