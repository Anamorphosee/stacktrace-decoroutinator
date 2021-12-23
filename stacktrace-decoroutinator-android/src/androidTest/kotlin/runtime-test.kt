package dev.reformator.stacktracedecoroutinator.runtime

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.reformator.stacktracedecoroutinator.utils.checkStacktrace
import dev.reformator.stacktracedecoroutinator.utils.getLineNumber
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

class TestException(message: String): Exception(message)

@RunWith(AndroidJUnit4::class)
class RuntimeTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun init() {
            DecoroutinatorRuntime.load()
        }
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

    private val feature = AtomicReference<CompletableFuture<Unit>?>()
    private var recBaseLineNumber: Int = 0
    private val allowedLineNumberOffsets = listOf(0, 1, 3, 4, 6)

    private suspend fun rec(lineNumberOffsets: List<Int>, index: Int): String {
        val checkedStacktrace = lineNumberOffsets.subList(0, index).reversed().map {
            StackTraceElement(
                RuntimeTest::class.java.typeName,
                "rec",
                "runtime-test.kt",
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
            "runtime-test.kt",
            lineNumber
        ))
        tailCallDeoptimize()
    }

    private suspend fun overload(par: String) {
        val lineNumber = getLineNumber() + 1
        suspendResumeAndCheckStack(StackTraceElement(
            RuntimeTest::class.java.typeName,
            "overload",
            "runtime-test.kt",
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