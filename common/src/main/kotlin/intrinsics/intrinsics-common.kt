@file:Suppress("PackageDirectoryMismatch")

package dev.reformator.stacktracedecoroutinator.common.intrinsics

import dev.reformator.bytecodeprocessor.intrinsics.ChangeClassName
import dev.reformator.bytecodeprocessor.intrinsics.SkipInvocations
import dev.reformator.bytecodeprocessor.intrinsics.fail

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
