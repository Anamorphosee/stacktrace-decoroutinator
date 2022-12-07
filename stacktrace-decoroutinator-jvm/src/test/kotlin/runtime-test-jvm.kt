@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.runtime

import dev.reformator.stacktracedecoroutinator.common.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.common.isDecoroutinatorBaseContinuation
import dev.reformator.stacktracedecoroutinator.jvmagentcommon.DecoroutinatorJvmAgentDebugMetadataInfoResolveStrategy
import dev.reformator.stacktracedecoroutinator.utils.checkStacktrace
import dev.reformator.stacktracedecoroutinator.utils.getLineNumber
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.future.await
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resumeWithException
import kotlin.random.Random
import kotlin.test.*

private const val FILE_NAME = "runtime-test-jvm.kt"

class TestException(message: String): Exception(message)

// Jacoco only instruments this class
class JacocoInstrumentedMethodTest {
    @BeforeTest
    fun setup() {
        System.setProperty(
            "dev.reformator.stacktracedecoroutinator.jvmAgentDebugMetadataInfoResolveStrategy",
            DecoroutinatorJvmAgentDebugMetadataInfoResolveStrategy.SYSTEM_RESOURCE.name
        )
        DecoroutinatorRuntime.load()
    }

    //@Disabled
    @Test
    fun jacocoInstrumentedMethodTest(): Unit = runBlocking {
        suspend fun jacocoInstrumentedMethod() {
            yield()
            yield()
        }

        jacocoInstrumentedMethod()
    }
}

class ReloadBaseContinuationTest {
    @Test
    fun reloadBaseContinuation() {
        val baseContinuationClass = Class.forName(BASE_CONTINUATION_CLASS_NAME)
        assertFalse(baseContinuationClass.isDecoroutinatorBaseContinuation)
        DecoroutinatorRuntime.load()
        assertTrue(baseContinuationClass.isDecoroutinatorBaseContinuation)
    }
}

class RuntimeTest {
    @BeforeTest
    fun setup() {
        System.setProperty(
            "dev.reformator.stacktracedecoroutinator.jvmAgentDebugMetadataInfoResolveStrategy",
            DecoroutinatorJvmAgentDebugMetadataInfoResolveStrategy.SYSTEM_RESOURCE.name
        )
        DecoroutinatorRuntime.load()
    }

    @Test
    fun basic() = runBlocking {
        val random = Random(123)
        val size = 30
        val lineNumberOffsets = generateSequence {
                allowedLineNumberOffsets[random.nextInt(allowedLineNumberOffsets.size)]
            }
            .take(size)
            .toList()
        val job = launch {
            val result = rec(lineNumberOffsets, 0)
            val expectedResult = (0 until size).joinToString(separator = " ", postfix = " ")
            assertEquals(expectedResult, result)
        }
        while (feature.get() == null) {
            delay(10)
        }
        feature.get()!!.complete(Unit)
        job.join()
    }

    @Test
    fun overloadedMethods() = runBlocking {
        overload(1)
        overload("")
    }

    @Test
    fun resumeWithException() {
        try {
            runBlocking {
                resumeWithExceptionRec(10)
            }
        } catch (e: RuntimeException) {
            e.printStackTrace()
            (1..10).forEach {
                assertEquals(StackTraceElement(
                    RuntimeTest::class.java.typeName,
                    "resumeWithExceptionRec",
                    FILE_NAME,
                    resumeWithExceptionRecBaseLineNumber + 8
                ), e.stackTrace[it])
            }
        }
    }

    @Test
    fun testLoadSelfDefinedClass() {
        Class.forName("io.ktor.utils.io.ByteBufferChannel")
    }

    @Test
    fun testSuspendCrossinlineInDifferentFile() {
        val flow = flow {
            for (i in 2..6) {
                emit(i)
                delay(10)
                emit(i * i * i)
            }
        }.transform {
            emit(it)
            delay(10)
            emit(it * it * it)
        }.transform {
            emit(it.toString())
            if (it == 5 * 5 * 5) {
                throw Exception("check")
            }
        }
        try {
            runBlocking {
                flow.collect { }
            }
        } catch (e: Exception) {
            assertEquals("check", e.message)
        }
    }

    private var resumeWithExceptionRecBaseLineNumber: Int = 0

    private suspend fun resumeWithExceptionRec(depth: Int) {
        resumeWithExceptionRecBaseLineNumber = getLineNumber()
        if (depth == 0) {
            suspendCancellableCoroutine<Unit> { continuation ->
                ForkJoinPool.commonPool().execute {
                    continuation.resumeWithException(RuntimeException("test"))
                }
            }
        } else {
            resumeWithExceptionRec(depth - 1)
        }
        tailCallDeoptimize()
    }

    private val feature = AtomicReference<CompletableFuture<Unit>?>()
    private var recBaseLineNumber: Int = 0
    private val allowedLineNumberOffsets = listOf(0, 1, 3, 4, 6)

    private suspend fun rec(lineNumberOffsets: List<Int>, index: Int): String {
        val checkedStacktrace = lineNumberOffsets.subList(0, index).reversed().map {
            StackTraceElement(
                RuntimeTest::class.java.typeName,
                "rec",
                FILE_NAME,
                recBaseLineNumber + it
            )
        }.toTypedArray()
        checkStacktrace(*checkedStacktrace)

        val message = if (index == lineNumberOffsets.size) {
            delay(10)
            val feature = CompletableFuture<Unit>()
            this.feature.set(feature)
            feature.await()
            ""
        } else {
            val previousMessage = try {
                recBaseLineNumber = getLineNumber() + 2
                val previousMessage = when (lineNumberOffsets[index]) {
                    0 -> rec(lineNumberOffsets, index + 1)
                    1 -> rec(lineNumberOffsets, index + 1)

                    3 -> rec(lineNumberOffsets, index + 1)
                    4 -> rec(lineNumberOffsets, index + 1)

                    6 -> rec(lineNumberOffsets, index + 1)

                    else -> throw IllegalArgumentException(
                        "invalid line number offset: ${lineNumberOffsets[index]} on index $index"
                    )
                }
                assertTrue(index % 2 == 1)
                previousMessage
            } catch (e: TestException) {
                assertTrue(index % 2 == 0)
                e.message!!
            }
            "$index $previousMessage"
        }

        checkStacktrace(*checkedStacktrace)

        if (index % 2 == 0) {
            return message
        } else {
            throw TestException(message)
        }
    }

    private suspend fun overload(@Suppress("UNUSED_PARAMETER") par: Int) {
        val lineNumber = getLineNumber() + 1
        suspendResumeAndCheckStack(StackTraceElement(
            RuntimeTest::class.java.typeName,
            "overload",
            FILE_NAME,
            lineNumber
        ))
        tailCallDeoptimize()
    }

    private suspend fun overload(@Suppress("UNUSED_PARAMETER") par: String) {
        val lineNumber = getLineNumber() + 1
        suspendResumeAndCheckStack(StackTraceElement(
            RuntimeTest::class.java.typeName,
            "overload",
            FILE_NAME,
            lineNumber
        ))
        tailCallDeoptimize()
    }

    private suspend fun suspendResumeAndCheckStack(vararg elements: StackTraceElement) {
        delay(10)
        checkStacktrace(*elements)
    }

    private fun tailCallDeoptimize() { }
}
