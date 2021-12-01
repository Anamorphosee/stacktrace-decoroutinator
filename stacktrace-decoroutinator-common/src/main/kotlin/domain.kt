package dev.reformator.stacktracedecoroutinator

data class DecoroutinatorMethodSpec(
    val methodName: String,
    val label2LineNumber: Map<Int, Int>
)

data class DecoroutinatorClassSpec(
    val sourceFileName: String?,
    val continuationClassName2Method: Map<String, DecoroutinatorMethodSpec>
)
