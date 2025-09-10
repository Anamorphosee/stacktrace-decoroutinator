@file:Suppress("PackageDirectoryMismatch", "unused")

package dev.reformator.stacktracedecoroutinator.test

import dev.reformator.bytecodeprocessor.intrinsics.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.future.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.net.URI
import java.net.URLClassLoader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
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
    fun inlineTransformedClassForKotlinc() {
        runBlocking {
            flowOf(1)
                .transform { emit(it) }
                .collect()
        }
    }

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
            e.stackTrace.checkStacktrace(*(1 .. 10).map {
                StackTraceElement(
                    RuntimeTest::class.java.typeName,
                    RuntimeTest::resumeWithExceptionRec.name,
                    currentFileName,
                    resumeWithExceptionRecBaseLineNumber + 8
                )
            }.toTypedArray())
        }
    }

    class CustomEx(): Exception() {
        var rethrow = false
    }

    @Junit4Test @Junit5Test
    fun resumeDoubleException() {
        var firstResumeLineNumber = 0
        var secondResumeLineNumber = 0
        var resumeClassName = ""
        var resumeMethodName = ""
        try {
            runBlocking {
                try {
                    resumeClassName = ownerClass.name
                    resumeMethodName = ownerMethodName
                    firstResumeLineNumber = currentLineNumber + 1
                    suspendCoroutineUninterceptedOrReturn { cont ->
                        cont.resumeWithException(CustomEx())
                        COROUTINE_SUSPENDED
                    }
                } catch (e: CustomEx) {
                    e.rethrow = true
                    secondResumeLineNumber = currentLineNumber + 1
                    suspendCoroutineUninterceptedOrReturn { cont ->
                        cont.resumeWithException(e)
                        COROUTINE_SUSPENDED
                    }
                }
            }
        } catch (e: CustomEx) {
            assertTrue(e.rethrow)
            val trace = e.stackTrace
            val secondResumeTraceStart = trace.getNextBoundaryIndex() + 1
            trace.checkStacktrace(
                StackTraceElement(
                    resumeClassName,
                    resumeMethodName,
                    currentFileName,
                    firstResumeLineNumber
                ),
                fromIndex = secondResumeTraceStart
            )
            val firstResumeTraceStart = trace.getNextBoundaryIndex(secondResumeTraceStart) + 1
            trace.checkStacktrace(
                StackTraceElement(
                    resumeClassName,
                    resumeMethodName,
                    currentFileName,
                    secondResumeLineNumber
                ),
                fromIndex = firstResumeTraceStart
            )
        }
    }

    private fun Array<StackTraceElement>.getNextBoundaryIndex(startIndex: Int = 0): Int {
        for (i in startIndex .. lastIndex) {
            val fileName = this[i].fileName
            if (fileName == "decoroutinator-boundary") return i
        }
        error("not found")
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
    fun loadInterfaceWithSuspendFunWithDefaultImpl() = runBlocking {
        object: InterfaceWithDefaultMethod { }.startCheck()
    }

    @Junit4Test @Junit5Test
    fun flowSingle(): Unit = runBlocking {
        val flow = flow {
            emit(10)
            yield()
        }
        flow.single()
    }

    @Junit4Test @Junit5Test
    fun concurrentTest() {
        val numThreads = Runtime.getRuntime().availableProcessors() * 2
        val numMocks = 10
        val random = Random(123)
        val tasks = List(numThreads) {
            val mocks = random.getConcurrentTestMocks(numMocks)
            Runnable {
                runBlocking {
                    callInline(mocks)
                }
            }
        }
        Executors.newFixedThreadPool(numThreads).let { executor ->
            try {
                val futures = tasks.map { executor.submit(it) }
                futures.forEach { it.get() }
            } finally {
                executor.shutdown()
            }
        }
    }

    private var resumeWithExceptionRecBaseLineNumber: Int = 0

    private suspend fun resumeWithExceptionRec(depth: Int) {
        resumeWithExceptionRecBaseLineNumber = currentLineNumber
        if (depth == 0) {
            suspendCancellableCoroutine { continuation ->
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


}

interface InterfaceWithDefaultMethod {
    suspend fun startCheck() {
        val lineNumber = currentLineNumber + 1
        check(StackTraceElement(
            InterfaceWithDefaultMethod::class.java.name,
            ownerMethodName,
            currentFileName,
            lineNumber
        ))
        tailCallDeoptimize()
    }

    suspend fun check(parent: StackTraceElement) {
        yield()
        checkStacktrace(parent)
    }
}

open class TailCallDeoptimizeTest {
    fun basic() = runBlocking {
        tailCallDeoptimizeBasicRec(recDepth)
    }

    fun interfaceWithDefaultMethodImpl() = runBlocking {
        object: InterfaceWithDefaultImplMethod {}.defaultImpl()
    }
}

open class CustomClassLoaderTest {
    @Junit5Test
    fun loadWithDecoroutinatorDependency() {
        loadCustomLoaderStubClass(true)
    }

    @Junit5Test
    fun loadWithoutDecoroutinatorDependency() {
        loadCustomLoaderStubClass(false)
    }

    fun basic(allowTailCallOptimization: Boolean) {
        val clazz = loadCustomLoaderStubClass(true)
        val instance = clazz.getDeclaredConstructor().newInstance()
        val performCheckMethod = clazz.getDeclaredMethod("performCheck", Boolean::class.javaPrimitiveType)
        performCheckMethod.invoke(instance, allowTailCallOptimization)
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
    @GetOwnerClass get() { fail() }

private val customLoaderJarUri: String
    @LoadConstant("customLoaderJarUri") get() { fail() }

private fun loadCustomLoaderStubClass(withDecoroutinatorDependency: Boolean): Class<*> =
    URLClassLoader(
        arrayOf(URI(customLoaderJarUri).toURL()),
        if (withDecoroutinatorDependency) ClassLoader.getSystemClassLoader() else null
    ).loadClass("dev.reformator.stacktracedecoroutinator.test.ClassWithSuspendFunctionsStub")

private fun tailCallDeoptimize() { }

interface InterfaceWithDefaultImplMethod {
    suspend fun defaultImpl() {
        val lineNumber = currentLineNumber + 1
        suspendFun(StackTraceElement(
            InterfaceWithDefaultImplMethod::class.java.typeName,
            ownerMethodName,
            currentFileName,
            lineNumber
        ))
    }

    suspend fun suspendFun(parent: StackTraceElement) {
        yield()
        checkStacktrace(parent)
    }
}

private interface ConcurrentTestMock {
    suspend fun call(trace: List<Class<out ConcurrentTestMock>>)
}

private suspend inline fun callInline(trace: List<Class<out ConcurrentTestMock>>) {
    yield()
    if (trace.isNotEmpty()) {
        trace[0].getDeclaredConstructor().newInstance().call(trace.subList(1, trace.size))
    }
}

const val CONCURRENT_TEST_MOCKS_NUMBER = 20
private class ConcurrentTestMock1: ConcurrentTestMock {
    override suspend fun call(trace: List<Class<out ConcurrentTestMock>>) = callInline(trace)
}
private class ConcurrentTestMock2: ConcurrentTestMock {
    override suspend fun call(trace: List<Class<out ConcurrentTestMock>>) = callInline(trace)
}
private class ConcurrentTestMock3: ConcurrentTestMock {
    override suspend fun call(trace: List<Class<out ConcurrentTestMock>>) = callInline(trace)
}
private class ConcurrentTestMock4: ConcurrentTestMock {
    override suspend fun call(trace: List<Class<out ConcurrentTestMock>>) = callInline(trace)
}
private class ConcurrentTestMock5: ConcurrentTestMock {
    override suspend fun call(trace: List<Class<out ConcurrentTestMock>>) = callInline(trace)
}
private class ConcurrentTestMock6: ConcurrentTestMock {
    override suspend fun call(trace: List<Class<out ConcurrentTestMock>>) = callInline(trace)
}
private class ConcurrentTestMock7: ConcurrentTestMock {
    override suspend fun call(trace: List<Class<out ConcurrentTestMock>>) = callInline(trace)
}
private class ConcurrentTestMock8: ConcurrentTestMock {
    override suspend fun call(trace: List<Class<out ConcurrentTestMock>>) = callInline(trace)
}
private class ConcurrentTestMock9: ConcurrentTestMock {
    override suspend fun call(trace: List<Class<out ConcurrentTestMock>>) = callInline(trace)
}
private class ConcurrentTestMock10: ConcurrentTestMock {
    override suspend fun call(trace: List<Class<out ConcurrentTestMock>>) = callInline(trace)
}
private class ConcurrentTestMock11: ConcurrentTestMock {
    override suspend fun call(trace: List<Class<out ConcurrentTestMock>>) = callInline(trace)
}
private class ConcurrentTestMock12: ConcurrentTestMock {
    override suspend fun call(trace: List<Class<out ConcurrentTestMock>>) = callInline(trace)
}
private class ConcurrentTestMock13: ConcurrentTestMock {
    override suspend fun call(trace: List<Class<out ConcurrentTestMock>>) = callInline(trace)
}
private class ConcurrentTestMock14: ConcurrentTestMock {
    override suspend fun call(trace: List<Class<out ConcurrentTestMock>>) = callInline(trace)
}
private class ConcurrentTestMock15: ConcurrentTestMock {
    override suspend fun call(trace: List<Class<out ConcurrentTestMock>>) = callInline(trace)
}
private class ConcurrentTestMock16: ConcurrentTestMock {
    override suspend fun call(trace: List<Class<out ConcurrentTestMock>>) = callInline(trace)
}
private class ConcurrentTestMock17: ConcurrentTestMock {
    override suspend fun call(trace: List<Class<out ConcurrentTestMock>>) = callInline(trace)
}
private class ConcurrentTestMock18: ConcurrentTestMock {
    override suspend fun call(trace: List<Class<out ConcurrentTestMock>>) = callInline(trace)
}
private class ConcurrentTestMock19: ConcurrentTestMock {
    override suspend fun call(trace: List<Class<out ConcurrentTestMock>>) = callInline(trace)
}
private class ConcurrentTestMock20: ConcurrentTestMock {
    override suspend fun call(trace: List<Class<out ConcurrentTestMock>>) = callInline(trace)
}

private fun Random.getConcurrentTestMocks(size: Int): List<Class<out ConcurrentTestMock>> =
    buildList(size) {
        val index = nextInt(CONCURRENT_TEST_MOCKS_NUMBER) + 1
        val className = "dev.reformator.stacktracedecoroutinator.test.ConcurrentTestMock$index"
        @Suppress("UNCHECKED_CAST")
        add(Class.forName(className, false, ConcurrentTestMock::class.java.classLoader) as Class<out ConcurrentTestMock>)
    }
