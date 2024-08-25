@file:Suppress("PackageDirectoryMismatch")
@file:JvmName("DecoroutinatorCommonApiKt")

package dev.reformator.stacktracedecoroutinator.common

import dev.reformator.stacktracedecoroutinator.common.internal.BASE_CONTINUATION_CLASS_NAME
import dev.reformator.stacktracedecoroutinator.common.internal.ENABLED_PROPERTY
import dev.reformator.stacktracedecoroutinator.common.internal.awakenerFileClass
import dev.reformator.stacktracedecoroutinator.common.internal.enabled
import unknownSpecClass
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn


data class DecoroutinatorStatus(val successful: Boolean, val description: String)

object DecoroutinatorRuntimeApi {


    /**
     * Get status of Decoroutinator correctness.
     * Must be called like this:
     * @param sourceCall call this lambda. Need to check status properly. See the sample below.
     * @sample getStatusSample
     */
    fun getStatus(
        allowTailCallOptimization: Boolean = false,
        sourceCall: suspend (callThisAndReturnItsResult: suspend () -> Any?) -> Any? = { it() }
    ): DecoroutinatorStatus {
        if (!enabled) {
            return DecoroutinatorStatus(
                successful = false,
                description = "Decoroutinator is forcefully disabled by setting runtime parameter [$ENABLED_PROPERTY]"
            )
        }

        val contunuation = object: Continuation<DecoroutinatorStatus> {
            var continuation: Continuation<Unit>? = null
            var wasSuspended = false
            var status: DecoroutinatorStatus? = null

            override val context: CoroutineContext = EmptyCoroutineContext

            override fun resumeWith(result: Result<DecoroutinatorStatus>) {
                require(wasSuspended)
                require(status == null)
                status = result.getOrThrow()
            }

            suspend fun suspendResumeAndGetStacktrace(): List<StackTraceElement> {
                suspendCoroutineUninterceptedOrReturn {
                    if (this.continuation != null) {
                        error("'sourceCall' must be called exactly once")
                    }
                    this.continuation = it
                    COROUTINE_SUSPENDED
                }
                return Exception().stackTrace.toList()
            }

            @Suppress("unused", "UNCHECKED_CAST")
            private suspend fun getStatus(): DecoroutinatorStatus {
                val trace = sourceCall {
                    suspendResumeAndGetStacktrace()
                }
                if (!wasSuspended) {
                    error("'sourceCall' must be called exactly once")
                }
                if (!(trace is List<*> && trace.all { it is StackTraceElement })) {
                    error("'sourceCall' must return the result of it's argument invocation")
                }
                trace as List<StackTraceElement>
                /*
                    trace should be from top to bottom:
                    - suspendResumeAndGetStacktrace()
                    - <awaking auxiliary frames>
                    - invoke()
                    - <invokeExact frames>
                    - <sourceCall>
                    - <invokeExact frames>
                    - getStatus()
                    - <invokeExact frames>
                    - awakener.awake()
                    - resumeWith()
                */
                val baseContinuationResumeIndex = trace.indexOfFirst {
                    it.className == BASE_CONTINUATION_CLASS_NAME && it.methodName == "resumeWith"
                }
                if (baseContinuationResumeIndex == -1) {
                    return DecoroutinatorStatus(
                        successful = false,
                        description = "Something wrong. [$BASE_CONTINUATION_CLASS_NAME.resumeWith()] method call is not found"
                    )
                }
                if (baseContinuationResumeIndex == 0) {
                    return DecoroutinatorStatus(
                        successful = false,
                        description = "Something wrong. [$BASE_CONTINUATION_CLASS_NAME.resumeWith()] method is on the top"
                    )
                }
                if (trace[0].methodName != "suspendResumeAndGetStacktrace") {
                    return DecoroutinatorStatus(
                        successful = false,
                        description = "Something wrong. The top call is [${trace[0].methodName}]"
                    )
                }
                val awakenerIndex = baseContinuationResumeIndex - 1
                if (trace[awakenerIndex].className != awakenerFileClass.name) {
                    return DecoroutinatorStatus(
                        successful = false,
                        description = "class [$BASE_CONTINUATION_CLASS_NAME] from Kotlin stdlib is not transformed"
                    )
                }
                val getStatusIndex = trace.indexOfFirst {
                    it.className == this.javaClass.name && it.methodName == "getStatus"
                }
                if (getStatusIndex == -1 || getStatusIndex >= awakenerIndex) {
                    return DecoroutinatorStatus(
                        successful = false,
                        description = "'getStatus' call is not found. Probably runtime lib is not transformed"
                    )
                }
                if (
                    trace.subList(0, baseContinuationResumeIndex).any {
                        it.className == unknownSpecClass.name
                    }
                ) {
                    return DecoroutinatorStatus(
                        successful = false,
                        description = "the stack trace contains unknown frames. Probably some local source is not transformed"
                    )
                }

                val invokeExactFramesCount = awakenerIndex - getStatusIndex - 1
                val awakingAuxiliaryFramesCount = 2
                val sourceCallFramesCount = getStatusIndex - invokeExactFramesCount - awakingAuxiliaryFramesCount - 1
                if (sourceCallFramesCount < 0) {
                    return DecoroutinatorStatus(
                        successful = false,
                        description = "Something wrong. The source call is $sourceCallFramesCount frames long"
                    )
                }
                if (!allowTailCallOptimization && sourceCallFramesCount == 0) {
                    return DecoroutinatorStatus(
                        successful = false,
                        description = "the stack trace doesn't contain source call frames. Probably the source call was tail call optimized"
                    )
                }

                return DecoroutinatorStatus(
                    successful = true,
                    description = "no issues detected"
                )
            }

            fun callGetStatus() {
                javaClass.getDeclaredMethod("getStatus", Continuation::class.java).invoke(this, this)
            }
        }

        contunuation.callGetStatus()
        contunuation.continuation.let {
            if (it == null) {
                error("'sourceCall' must only call its argument, no other actions")
            }
            contunuation.wasSuspended = true
            it.resumeWith(Result.success(Unit))
        }
        return contunuation.status.let {
            require(it != null)
            it
        }
    }
}

private fun getStatusSample() {
    val status = DecoroutinatorRuntimeApi.getStatus { it() }
    if (status.successful) {
        println("Decoroutinator is performing properly.")
    } else {
        println("Decoroutinator is not performing properly: ${status.description}.")
    }
}