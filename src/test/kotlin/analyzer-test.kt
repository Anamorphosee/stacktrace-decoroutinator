package dev.reformator.stacktracedecoroutinator.analyzer

import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals

private suspend fun privateSuspendFunction() {
    delay(100)
    println("test")
    delay(100)
}

suspend fun suspendFunction() {
    privateSuspendFunction()
    println("test")
}

interface TestInterface {
    suspend fun publicFun(par: Any?) {
        delay(100)
        println()
    }
}

open class SuperTestInterface: TestInterface {
    override suspend fun publicFun(par: Any?) {
        delay(100)
        println()
    }
}

private class PrivateClass: SuperTestInterface() {
    private suspend fun privateFun(par: Any?) {
        delay(100)
        println()
    }

    override suspend fun publicFun(par: Any?) {
        delay(100)
        println()
    }
}

class AnalyzerTest {
    private val analyzer = DecoroutinatorClassAnalyzerImpl()

    @Test
    fun getDecoroutinatorClassSpec() {
        val fileSpec =
            analyzer.getDecoroutinatorClassSpec(
                "dev.reformator.stacktracedecoroutinator.analyzer.Analyzer_testKt")
        assertEquals(DecoroutinatorClassSpec(
            sourceFileName = "analyzer-test.kt",
            continuationClassName2Method = mapOf(
                "dev.reformator.stacktracedecoroutinator.analyzer.Analyzer_testKt\$privateSuspendFunction\$1" to
                        DecoroutinatorMethodSpec(
                            methodName = "privateSuspendFunction",
                            label2LineNumber = mapOf(1 to 8U, 2 to 10U)
                        ),
                "dev.reformator.stacktracedecoroutinator.analyzer.Analyzer_testKt\$suspendFunction\$1" to
                        DecoroutinatorMethodSpec(
                            methodName = "suspendFunction",
                            label2LineNumber = mapOf(1 to 14U)
                        )
            )
        ), fileSpec)

        val privateClassSpec = analyzer.getDecoroutinatorClassSpec(
            "dev.reformator.stacktracedecoroutinator.analyzer.PrivateClass")
        assertEquals(DecoroutinatorClassSpec(
            sourceFileName = "analyzer-test.kt",
            continuationClassName2Method = mapOf(
                "dev.reformator.stacktracedecoroutinator.analyzer.PrivateClass\$privateFun\$1" to
                        DecoroutinatorMethodSpec(
                            methodName = "privateFun",
                            label2LineNumber = mapOf(1 to 34U)
                        ),
                "dev.reformator.stacktracedecoroutinator.analyzer.PrivateClass\$publicFun\$1" to
                        DecoroutinatorMethodSpec(
                            methodName = "publicFun",
                            label2LineNumber = mapOf(1 to 39U)
                        )
            )
        ), privateClassSpec)

        val testInterfaceSpec = analyzer.getDecoroutinatorClassSpec(
            "dev.reformator.stacktracedecoroutinator.analyzer.TestInterface\$DefaultImpls")
        assertEquals(DecoroutinatorClassSpec(
            sourceFileName = "analyzer-test.kt",
            continuationClassName2Method = mapOf(
                "dev.reformator.stacktracedecoroutinator.analyzer.TestInterface\$publicFun\$1" to
                        DecoroutinatorMethodSpec(
                            methodName = "publicFun",
                            label2LineNumber = mapOf(1 to 20U)
                        )
            )
        ), testInterfaceSpec)
    }

    @Test
    fun getClassNameByContinuationClassName() {
        assertEquals("dev.reformator.stacktracedecoroutinator.analyzer.Analyzer_testKt",
            analyzer.getClassNameByContinuationClassName(
                "dev.reformator.stacktracedecoroutinator.analyzer.Analyzer_testKt\$privateSuspendFunction\$1"
            )
        )
        assertEquals("dev.reformator.stacktracedecoroutinator.analyzer.Analyzer_testKt",
            analyzer.getClassNameByContinuationClassName(
                "dev.reformator.stacktracedecoroutinator.analyzer.Analyzer_testKt\$suspendFunction\$1"
            )
        )
        assertEquals("dev.reformator.stacktracedecoroutinator.analyzer.PrivateClass",
            analyzer.getClassNameByContinuationClassName(
                "dev.reformator.stacktracedecoroutinator.analyzer.PrivateClass\$privateFun\$1"
            )
        )
        assertEquals("dev.reformator.stacktracedecoroutinator.analyzer.PrivateClass",
            analyzer.getClassNameByContinuationClassName(
                "dev.reformator.stacktracedecoroutinator.analyzer.PrivateClass\$publicFun\$1"
            )
        )
        assertEquals("dev.reformator.stacktracedecoroutinator.analyzer.TestInterface\$DefaultImpls",
            analyzer.getClassNameByContinuationClassName(
                "dev.reformator.stacktracedecoroutinator.analyzer.TestInterface\$publicFun\$1"
            )
        )

    }
}