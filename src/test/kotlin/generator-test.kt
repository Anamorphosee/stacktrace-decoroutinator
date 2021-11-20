package dev.reformator.stacktracedecoroutinator.generator

import dev.reformator.stacktracedecoroutinator.analyzer.DecoroutinatorClassSpec
import dev.reformator.stacktracedecoroutinator.analyzer.DecoroutinatorMethodSpec
import dev.reformator.stacktracedecoroutinator.util.callStack
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import io.mockk.verifySequence
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.function.Function
import kotlin.RuntimeException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeneratorTest {
    private lateinit var classLoader: DecoroutinatorClassLoader
    private lateinit var continuationMock: (stackDepth: Int, result: Any?) -> Any?
    private lateinit var continuationHandleFunc: Function<Int, MethodHandle>

    @BeforeTest
    fun setup() {
        classLoader = DecoroutinatorClassLoader()
        continuationMock = mockk()
        val continuationMockHandle = MethodHandles.publicLookup().findVirtual(continuationMock::class.java,
            "invoke", MethodType.methodType(Object::class.java, Object::class.java, Object::class.java))
        continuationHandleFunc = Function<Int, MethodHandle> {
            MethodHandles.insertArguments(continuationMockHandle, 0, continuationMock, it)
        }
    }

    @Test
    fun basic() {
        val methodName2StacktraceHandler =
            classLoader.getMethodName2StacktraceHandlersMap("test.TestClass", DecoroutinatorClassSpec(
                sourceFileName = "source.test",
                continuationClassName2Method = mapOf(
                    "1.1" to DecoroutinatorMethodSpec(
                        methodName = "method1",
                        label2LineNumber = mapOf(
                            -3 to 3U,
                            -5 to 5U,
                            -8 to 8U,
                            -13 to 13U
                        )
                    ),
                    "1.2" to DecoroutinatorMethodSpec(
                        methodName = "method1",
                        label2LineNumber = mapOf(
                            -1 to 1U,
                            -2 to 2U,
                            -3 to 3U,
                            -5 to 5U
                        )
                    ),
                    "2.1" to DecoroutinatorMethodSpec(
                        methodName = "method2",
                        label2LineNumber = mapOf(
                            -3 to 3U,
                            -5 to 5U,
                            -8 to 8U,
                            -13 to 13U
                        )
                    ),
                    "3.1" to DecoroutinatorMethodSpec(
                        methodName = "method3",
                        label2LineNumber = mapOf(
                            -3 to 3U,
                            -5 to 5U,
                            -8 to 8U,
                            -13 to 13U
                        )
                    )
                )
            ))
        val method1 = methodName2StacktraceHandler["method1"]!!
        val method2 = methodName2StacktraceHandler["method2"]!!
        val method3 = methodName2StacktraceHandler["method3"]!!

        val stackHandles = arrayOf(
            method1, method2, method3, method3, method2, method1
        )
        val lineNumbers = intArrayOf(
            13, 8, 5, 8, 5, 1
        )

        every { continuationMock(5, "test5") } answers {
            checkStacktrace(
                StackTraceElement("test.TestClass", "method2", "source.test", 5),
                StackTraceElement("test.TestClass", "method3", "source.test", 8),
                StackTraceElement("test.TestClass", "method3", "source.test", 5),
                StackTraceElement("test.TestClass", "method2", "source.test", 8),
                StackTraceElement("test.TestClass", "method1", "source.test", 13)
            )
            "test4"
        }
        var depth4Exception: Exception? = null
        every { continuationMock(4, "test4") } answers {
            checkStacktrace(
                StackTraceElement("test.TestClass", "method3", "source.test", 8),
                StackTraceElement("test.TestClass", "method3", "source.test", 5),
                StackTraceElement("test.TestClass", "method2", "source.test", 8),
                StackTraceElement("test.TestClass", "method1", "source.test", 13)
            )
            Exception().also {
                depth4Exception = it
                throw it
            }
        }
        var depth3Result: Any? = null
        every { continuationMock(3, any()) } answers {
            checkStacktrace(
                StackTraceElement("test.TestClass", "method3", "source.test", 5),
                StackTraceElement("test.TestClass", "method2", "source.test", 8),
                StackTraceElement("test.TestClass", "method1", "source.test", 13)
            )
            depth3Result = call.invocation.args[1]
            Result.success(depth3Result).also {
                assertTrue(it.isFailure)
                assertEquals(depth4Exception, it.exceptionOrNull())
            }
            kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
        }

        assertEquals(kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED,
            method1.callStack(stackHandles, lineNumbers, 1, continuationHandleFunc, "test5"))

        verifySequence {
            continuationMock(5, "test5")
            continuationMock(4, "test4")
            continuationMock(3, depth3Result)
        }
    }
}

fun checkStacktrace(vararg elements: StackTraceElement) {
    RuntimeException().stackTrace.toList().also { stacktrace ->
        val startIndex = stacktrace.indexOf(elements[0])
        val checkedStacktrace = stacktrace.subList(startIndex, startIndex + elements.size)
        assertEquals(elements.toList(), checkedStacktrace)
    }
}
