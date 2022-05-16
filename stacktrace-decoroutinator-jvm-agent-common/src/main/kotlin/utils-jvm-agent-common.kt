package dev.reformator.stacktracedecoroutinator.jvmagentcommon

import dev.reformator.stacktracedecoroutinator.common.BASE_CONTINUATION_CLASS_NAME

val BASE_CONTINUATION_INTERNAL_CLASS_NAME = BASE_CONTINUATION_CLASS_NAME.replace('.', '/')
val REGISTER_LOOKUP_METHOD_NAME = "\$decoroutinatorRegisterLookup"

@Target(AnnotationTarget.CLASS)
@Retention
internal annotation class DecoroutinatorAgentTransformedMarker(
    val fileNamePresent: Boolean = true,
    val fileName: String = "",
    val methodNames: Array<String>,
    val lineNumbersCounts: IntArray,
    val lineNumbers: IntArray
)

val Class<*>.isDecoroutinatorAgentTransformed: Boolean
    get() = isAnnotationPresent(DecoroutinatorAgentTransformedMarker::class.java)
