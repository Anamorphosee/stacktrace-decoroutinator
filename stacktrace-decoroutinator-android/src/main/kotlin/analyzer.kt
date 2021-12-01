package dev.reformator.stacktracedecoroutinator.android.analyzer

import dev.reformator.stacktracedecoroutinator.DecoroutinatorClassSpec
import dev.reformator.stacktracedecoroutinator.analyzer.DecoroutinatorClassAnalyzer

class AndroidDecoroutinatorClassAnalyzer: DecoroutinatorClassAnalyzer {
    override fun getDecoroutinatorClassSpec(className: String): DecoroutinatorClassSpec {
        TODO("Not yet implemented")
    }

    override fun getClassNameByContinuationClassName(coroutineClassName: String): String {
        TODO("Not yet implemented")
    }

}

object Hui: () -> String {
    override fun invoke(): String {
        return "hui"
    }
}