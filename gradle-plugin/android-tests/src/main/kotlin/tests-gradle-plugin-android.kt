@file:Suppress("PackageDirectoryMismatch", "JUnitMixedFramework")

package dev.reformator.stacktracedecoroutinator.gradlepluginandroidtests

import android.Manifest
import androidx.test.rule.GrantPermissionRule
import dev.reformator.bytecodeprocessor.intrinsics.LoadConstant
import dev.reformator.bytecodeprocessor.intrinsics.currentFileName
import dev.reformator.bytecodeprocessor.intrinsics.currentLineNumber
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.aarbuilder.suspendFunFromAar
import dev.reformator.stacktracedecoroutinator.aarbuilder.suspendFunFromAarFileName
import dev.reformator.stacktracedecoroutinator.aarbuilder.suspendFunFromAarLineNumber
import dev.reformator.stacktracedecoroutinator.aarbuilder.suspendFunFromAarMethodName
import dev.reformator.stacktracedecoroutinator.aarbuilder.suspendFunFromAarOwnerClassName
import dev.reformator.stacktracedecoroutinator.test.checkStacktrace
import dev.reformator.stacktracedecoroutinator.test.setRetraceMappingFiles
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.coroutines.resume

open class TestLocalFile {
    @get:Rule
    val permissionRule: GrantPermissionRule
        get() = GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE)

    @Before
    fun setup() {
        setRetraceMappingFiles(minifyDebugMappingFile)
    }

    @Test
    fun localTest(): Unit = runBlocking {
        fun1()
    }

    @Test
    fun suspendFunFromAarTest() = runBlocking {
        suspendFunFromAar {
            yield()
            checkStacktrace(StackTraceElement(
                suspendFunFromAarOwnerClassName,
                suspendFunFromAarMethodName,
                suspendFunFromAarFileName,
                suspendFunFromAarLineNumber
            ))
        }
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
    @get:Rule
    val permissionRule: GrantPermissionRule
        get() = GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE)

    @Before
    fun setup() {
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
    @get:Rule
    val permissionRule: GrantPermissionRule
        get() = GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE)

    @Before
    fun setup() {
        setRetraceMappingFiles(minifyDebugMappingFile)
    }
}

@get:LoadConstant("minifyDebugMappingFile")
private val minifyDebugMappingFile: String
    get() = fail()
