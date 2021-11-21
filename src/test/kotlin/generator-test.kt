package dev.reformator.stacktracedecoroutinator.generator

import dev.reformator.stacktracedecoroutinator.analyzer.DecoroutinatorClassSpec
import dev.reformator.stacktracedecoroutinator.analyzer.DecoroutinatorMethodSpec
import dev.reformator.stacktracedecoroutinator.util.callStack
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import java.util.function.BiFunction
import kotlin.RuntimeException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GeneratorTest {
    private lateinit var classLoader: DecoroutinatorClassLoader
    private lateinit var continuationMock: BiFunction<Int, Any?, Any?>

    @BeforeTest
    fun setup() {
        classLoader = DecoroutinatorClassLoader()
        continuationMock = mockk()
    }

    @Test
    fun basic() {
        val methodName2StacktraceHandler =
            classLoader.getMethodName2StacktraceHandlerMap("test.TestClass", DecoroutinatorClassSpec(
                sourceFileName = "source.test",
                continuationClassName2Method = mapOf(
                    "1.1" to DecoroutinatorMethodSpec(
                        methodName = "method1",
                        label2LineNumber = mapOf(
                            -3 to 3,
                            -5 to 5,
                            -8 to 8,
                            -13 to 13
                        )
                    ),
                    "1.2" to DecoroutinatorMethodSpec(
                        methodName = "method1",
                        label2LineNumber = mapOf(
                            -1 to 1,
                            -2 to 2,
                            -3 to 3,
                            -5 to 5
                        )
                    ),
                    "2.1" to DecoroutinatorMethodSpec(
                        methodName = "method2",
                        label2LineNumber = mapOf(
                            -3 to 3,
                            -5 to 5,
                            -8 to 8,
                            -13 to 13
                        )
                    ),
                    "3.1" to DecoroutinatorMethodSpec(
                        methodName = "method3",
                        label2LineNumber = mapOf(
                            -3 to 3,
                            -5 to 5,
                            -8 to 8,
                            -13 to 13
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

        every { continuationMock.apply(6, "test6") } answers {
            checkStacktrace(
                StackTraceElement("test.TestClass", "method1", "source.test", 1),
                StackTraceElement("test.TestClass", "method2", "source.test", 5),
                StackTraceElement("test.TestClass", "method3", "source.test", 8),
                StackTraceElement("test.TestClass", "method3", "source.test", 5),
                StackTraceElement("test.TestClass", "method2", "source.test", 8),
                StackTraceElement("test.TestClass", "method1", "source.test", 13)
            )
            "test5"
        }
        every { continuationMock.apply(5, "test5") } answers {
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
        every { continuationMock.apply(4, "test4") } answers {
            checkStacktrace(
                StackTraceElement("test.TestClass", "method3", "source.test", 8),
                StackTraceElement("test.TestClass", "method3", "source.test", 5),
                StackTraceElement("test.TestClass", "method2", "source.test", 8),
                StackTraceElement("test.TestClass", "method1", "source.test", 13)
            )
            Exception().also {
                depth4Exception = it
            }
        }
        every { continuationMock.apply(3, any()) } answers {
            checkStacktrace(
                StackTraceElement("test.TestClass", "method3", "source.test", 5),
                StackTraceElement("test.TestClass", "method2", "source.test", 8),
                StackTraceElement("test.TestClass", "method1", "source.test", 13)
            )
            assertEquals(depth4Exception, call.invocation.args[1])
            kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
        }

        assertEquals(kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED,
            callStack(stackHandles, lineNumbers, continuationMock, "test6"))

        verifySequence {
            continuationMock.apply(6, "test6")
            continuationMock.apply(5, "test5")
            continuationMock.apply(4, "test4")
            continuationMock.apply(3, depth4Exception)
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
