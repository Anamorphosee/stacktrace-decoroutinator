@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.intrinsics

import dev.reformator.bytecodeprocessor.intrinsics.ChangeClassName
import dev.reformator.bytecodeprocessor.intrinsics.ChangeInvocationsOwner
import dev.reformator.bytecodeprocessor.intrinsics.SkipInvocations
import dev.reformator.bytecodeprocessor.intrinsics.fail
import dev.reformator.stacktracedecoroutinator.common.internal.DEBUG_METADATA_CLASS_NAME
import kotlin.coroutines.Continuation

@ChangeClassName(
    toName = DEBUG_METADATA_CLASS_NAME,
    deleteAfterChanging = true
)
@Target(AnnotationTarget.CLASS)
internal annotation class DebugMetadata(
    @get:JvmName("f")
    val sourceFile: String = "",
    @get:JvmName("l")
    val lineNumbers: IntArray = [],
    @get:JvmName("m")
    val methodName: String = "",
    @get:JvmName("c")
    val className: String = ""
)

@ChangeClassName(
    toName = "kotlin.Result\$Failure",
    deleteAfterChanging = true
)
internal class FailureResult {
    @JvmField val exception: Throwable = fail()
}

@ChangeClassName(
    toName = "kotlin._Assertions",
    deleteAfterChanging = true
)
@PublishedApi
internal object _Assertions {
    @JvmField
    val ENABLED: Boolean = fail()
}

@Suppress("UnusedReceiverParameter")
internal val Any?.toResult: Result<*>
    @SkipInvocations get() { fail() }

@Suppress("UNUSED_PARAMETER")
@ChangeInvocationsOwner(
    toName = "kotlin.ResultKt",
    deleteAfterChanging = true
)
@PublishedApi
internal fun createFailure(exception: Throwable): Any { fail() }

@Suppress("UNUSED_PARAMETER")
@ChangeInvocationsOwner(
    toName = "kotlin.coroutines.jvm.internal.DebugProbesKt",
    deleteAfterChanging = true
)
@PublishedApi
internal fun probeCoroutineResumed(frame: Continuation<*>) { fail() }
