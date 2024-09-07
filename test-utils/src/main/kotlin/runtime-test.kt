package dev.reformator.stacktracedecoroutinator.test

import dev.reformator.bytecodeprocessor.intrinsics.GetOwnerClass
import dev.reformator.bytecodeprocessor.intrinsics.currentFileName
import dev.reformator.bytecodeprocessor.intrinsics.currentLineNumber
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.common.DecoroutinatorCommonApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.future.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random
import kotlin.reflect.KFunction

class TestException(message: String): Exception(message)

@Suppress("JUnitMixedFramework")
open class RuntimeTest {
    @Junit4Test @Junit5Test
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

    @Junit4Test @Junit5Test
    fun overloadedMethods() = runBlocking {
        overload(1)
        overload("")
    }

    @Junit4Test @Junit5Test
    fun resumeWithException() {
        try {
            runBlocking {
                resumeWithExceptionRec(10)
            }
        } catch (e: RuntimeException) {
            (1..10).forEach {
                assertEquals(StackTraceElement(
                    RuntimeTest::class.java.typeName,
                    RuntimeTest::resumeWithExceptionRec.name,
                    currentFileName,
                    resumeWithExceptionRecBaseLineNumber + 8
                ), e.stackTrace[it])
            }
            assertEquals(".(boundary)", e.stackTrace[12].toString())
        }
    }

    @Junit4Test @Junit5Test
    fun testLoadSelfDefinedClass() {
        Class.forName("io.ktor.utils.io.ByteBufferChannel")
    }

    @Junit4Test @Junit5Test
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

    @Junit4Test @Junit5Test
    fun testCommonApiStatus() {
        val status = DecoroutinatorCommonApi.getStatus(allowTailCallOptimization = true)
        assertTrue(status.successful, status.description)
    }

    private var resumeWithExceptionRecBaseLineNumber: Int = 0

    private suspend fun resumeWithExceptionRec(depth: Int) {
        resumeWithExceptionRecBaseLineNumber = currentLineNumber
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
                RuntimeTest::rec.name,
                currentFileName,
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
                recBaseLineNumber = currentLineNumber + 2
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

    @Suppress("UNUSED_PARAMETER")
    private suspend fun overload(par: Int) {
        val lineNumber = currentLineNumber + 1
        suspendResumeAndCheckStack(StackTraceElement(
            RuntimeTest::class.java.typeName,
            run {val x: suspend (Int) -> Unit = ::overload; x as KFunction<*>}.name,
            currentFileName,
            lineNumber
        ))
        tailCallDeoptimize()
    }

    @Suppress("UNUSED_PARAMETER")
    private suspend fun overload(par: String) {
        val lineNumber = currentLineNumber + 1
        suspendResumeAndCheckStack(StackTraceElement(
            RuntimeTest::class.java.typeName,
            run {val x: suspend (String) -> Unit = ::overload; x as KFunction<*>}.name,
            currentFileName,
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

open class TailCallDeoptimizeTest {
    fun basic() = runBlocking {
        tailCallDeoptimizeBasicRec(recDepth)
    }

    fun testCommonApiStatus() {
        val status = DecoroutinatorCommonApi.getStatus(allowTailCallOptimization = true)
        assertTrue(status.successful, status.description)
    }
}

private const val recDepth = 10
private val recLineNumber = currentLineNumber + 3
suspend fun tailCallDeoptimizeBasicRec(depth: Int) {
    if (depth > 0) {
        tailCallDeoptimizeBasicRecRec(depth - 1)
    } else {
        suspendCoroutineUninterceptedOrReturn { cont ->
            ForkJoinPool.commonPool().execute {
                cont.resume(Unit)
            }
            COROUTINE_SUSPENDED
        }
        val checkedStacktrace = (0 until recDepth * 2).map {
            val (methodName, lineNumber) = if (it % 2 == 0) {
                ::tailCallDeoptimizeBasicRecRec.name to recRecLineNumber
            } else {
                ::tailCallDeoptimizeBasicRec.name to recLineNumber
            }
            StackTraceElement(
                currentFileClass.name,
                methodName,
                currentFileName,
                lineNumber
            )
        }.toTypedArray()
        checkStacktrace(*checkedStacktrace)
    }
}

private val recRecLineNumber = currentLineNumber + 2
suspend fun tailCallDeoptimizeBasicRecRec(depth: Int) {
    tailCallDeoptimizeBasicRec(depth)
}


private val currentFileClass: Class<*>
    @GetOwnerClass(deleteAfterModification = true) get() { fail() }
