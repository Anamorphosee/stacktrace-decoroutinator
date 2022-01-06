package dev.reformator.stacktracedecoroutinator.runtime

import dev.reformator.stacktracedecoroutinator.utils.checkStacktrace
import dev.reformator.stacktracedecoroutinator.utils.getLineNumber
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ForkJoinPool
import kotlin.test.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resumeWithException
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val fileName = "runtime-test-jvm.kt"

class TestException(message: String): Exception(message)

class RuntimeTest {
    @BeforeTest
    fun setup() {
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
                    fileName,
                    resumeWithExceptionRecBaseLineNumber + 8
                ), e.stackTrace[it])
            }
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
                fileName,
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

    private suspend fun overload(par: Int) {
        val lineNumber = getLineNumber() + 1
        suspendResumeAndCheckStack(StackTraceElement(
            RuntimeTest::class.java.typeName,
            "overload",
            fileName,
            lineNumber
        ))
        tailCallDeoptimize()
    }

    private suspend fun overload(par: String) {
        val lineNumber = getLineNumber() + 1
        suspendResumeAndCheckStack(StackTraceElement(
            RuntimeTest::class.java.typeName,
            "overload",
            fileName,
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
