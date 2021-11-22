package dev.reformator.stacktracedecoroutinator.continuation

import checkStacktrace
import dev.reformator.stacktracedecoroutinator.util.DocoroutinatorRuntime
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestException(message: String): Exception(message)

class ContinuationTest {
    private suspend fun rec(lineNumbers: List<Int>, future: CompletionStage<Unit>, index: Int): String {
        val checkedStacktrace = lineNumbers.subList(0, index).reversed().map {
            StackTraceElement(
                "dev.reformator.stacktracedecoroutinator.continuation.ContinuationTest",
                "rec",
                "continuation-test.kt",
                it
            )
        }.toTypedArray()
        checkStacktrace(*checkedStacktrace)

        val message = if (index == lineNumbers.size) {
            future.await()
            ""
        } else {
            val previousMessage = try {
                val previousMessage = when (lineNumbers[index]) {
                    36 -> rec(lineNumbers, future, index + 1)
                    37 -> rec(lineNumbers, future, index + 1)

                    39 -> rec(lineNumbers, future, index + 1)
                    40 -> rec(lineNumbers, future, index + 1)

                    42 -> rec(lineNumbers, future, index + 1)

                    else -> throw IllegalArgumentException("invalid line number: ${lineNumbers[index]} on index $index")
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

    private val allowedLineNumber = listOf(36, 37, 39, 40, 42)

    @BeforeTest
    fun setup() {
        DocoroutinatorRuntime().enableDecoroutinatorRuntime()
    }

    @Test
    fun basic() = runBlocking {
        val random = Random(123)
        val size = 30
        val lineNumbers = generateSequence { allowedLineNumber[random.nextInt(allowedLineNumber.size)] }
            .take(size)
            .toList()
        val future = CompletableFuture<Unit>()
        val job = launch {
            val result = rec(lineNumbers, future, 0)
            val expectedResult = (0 until size).joinToString(separator = " ", postfix = " ")
            assertEquals(expectedResult, result)
        }
        future.complete(Unit)
        job.join()
    }
}