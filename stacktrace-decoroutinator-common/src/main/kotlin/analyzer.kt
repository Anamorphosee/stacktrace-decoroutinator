package dev.reformator.stacktracedecoroutinator.analyzer

import dev.reformator.stacktracedecoroutinator.DecoroutinatorClassSpec

interface DecoroutinatorClassAnalyzer {
    fun getDecoroutinatorClassSpec(className: String): DecoroutinatorClassSpec
    fun getClassNameByContinuationClassName(coroutineClassName: String): String
}
